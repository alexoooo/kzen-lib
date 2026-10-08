package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import javax.tools.ToolProvider
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * A process-wide registry (kzen-auto's `JobDataValues.adapters`) outlives the class loaders of the values it lifts:
 * a compiled expression's loader is meant to be released when its cache evicts it, so describing or lifting a value
 * of a class that loader defines must not keep the loader reachable — while a class that stays loaded still hits
 * the cache, which on the per-row lift path takes no allocation.
 */
class NativeTypeResolverCacheTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val compiled = compileFixtures()

    @Volatile
    private var allocationSink: ByteArray? = null

    @AfterTest
    fun deleteFixtures() {
        compiled.deleteRecursively()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun loaderOfDescribedAndLiftedClassesIsCollectable() {
        DefaultDataAdapterRegistry().use { registry ->
            assertCollectable(loadOnly(), "a loader nothing describes")
            assertCollectable(describeAndLift(registry), "a loader whose classes were described and lifted")
        }
    }


    @Test
    fun loaderOfOpaqueValueLiftedAgainstItsNativeIsCollectable() {
        DefaultDataAdapterRegistry().use { registry ->
            assertCollectable(liftOpaque(registry), "a loader whose opaque value was lifted against its native")
        }
    }


    @Test
    fun loaderOfResolvedOwnerIsCollectable() {
        DefaultNativeTypeResolver().use { resolver ->
            assertCollectable(resolveThroughOwner(resolver), "a loader an owner resolved through")
        }
    }


    @Test
    fun typeOverSiblingLoadersKeepsNeitherAlive() {
        DefaultDataAdapterRegistry().use { registry ->
            fixtureLoader().use { kept ->
                val (dropped, described) = describeMapWithDropped(registry, kept.loadClass("release.Item"))
                assertCollectable(dropped, "a sibling loader named alongside a live one")
                assertTrue(described.structural is DataType.Opaque, "a record-keyed map is opaque")
            }
        }
    }


    @Test
    fun applicationAndLiveLoaderClassesStayCached() {
        DefaultNativeTypeResolver().use { resolver ->
            assertSame(resolver.describe(typeOf<ApplicationRecord>()), resolver.describe(typeOf<ApplicationRecord>()))
            assertSame(resolver.describe(typeOf<List<String>>()), resolver.describe(typeOf<List<String>>()))
            val applicationContract = resolver.describe(typeOf<ApplicationRecord>()).contract
            assertSame(
                resolver.resolve(applicationContract, ApplicationRecord::class.java),
                resolver.resolve(applicationContract, String::class.java))

            fixtureLoader().use { loader ->
                val item = loader.loadClass("release.Item")
                val listOfItems = List::class.createType(listOf(KTypeProjection.invariant(item.kotlin.createType())))
                assertSame(resolver.describe(item.kotlin.createType()), resolver.describe(item.kotlin.createType()))
                assertSame(resolver.describe(listOfItems), resolver.describe(listOfItems))
                val itemContract = resolver.describe(item.kotlin.createType()).contract
                assertSame(resolver.resolve(itemContract, item), resolver.resolve(itemContract, item))
            }
        }
    }


    @Test
    fun cacheHitsAllocateNothing() {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        if (!bean.isThreadAllocatedMemorySupported) {
            return
        }
        bean.isThreadAllocatedMemoryEnabled = true
        DefaultNativeTypeResolver().use { resolver ->
            fixtureLoader().use { loader ->
                val item = loader.loadClass("release.Item")
                val itemType = item.kotlin.createType()
                val listOfItems = List::class.createType(listOf(KTypeProjection.invariant(itemType)))
                val applicationType = typeOf<ApplicationRecord>()
                val itemContract = resolver.describe(itemType).contract
                val applicationContract = resolver.describe(applicationType).contract
                val describeHits = listOf(itemType, listOfItems, applicationType)
                for (type in describeHits) {
                    val allocated = allocatedBy(bean) { resolver.describe(type) }
                    assertTrue(allocated < measuredHits, "describing $type: $allocated bytes over $measuredHits hits")
                }

                // A resolution is keyed by its contract, whose hash allocates; finding the owner's map must add nothing
                val resolveHits = listOf(itemContract to item, applicationContract to ApplicationRecord::class.java)
                for ((contract, owner) in resolveHits) {
                    val contractKeyed = ConcurrentHashMap(mapOf(contract to resolver.resolve(contract, owner)))
                    val allocated = allocatedBy(bean) { resolver.resolve(contract, owner) } -
                            allocatedBy(bean) { contractKeyed[contract] }
                    assertTrue(allocated < measuredHits,
                        "resolving in $owner: $allocated bytes over $measuredHits hits")
                }
            }
        }
    }


    /** The least [hit] allocated in any of [measuredRounds], so that a round the JIT is still compiling is ignored. */
    private fun allocatedBy(bean: ThreadMXBean, hit: () -> Unit): Long {
        repeat(warmupHits) { hit() }
        val thread = Thread.currentThread().threadId()
        return (1..measuredRounds).minOf {
            val before = bean.getThreadAllocatedBytes(thread)
            repeat(measuredHits) { hit() }
            bean.getThreadAllocatedBytes(thread) - before
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class ApplicationRecord(val name: String)


    private fun loadOnly(): WeakReference<ClassLoader> {
        val loader = fixtureLoader()
        loader.loadClass("release.Item")
        loader.close()
        return WeakReference(loader)
    }


    private fun describeAndLift(registry: DefaultDataAdapterRegistry): WeakReference<ClassLoader> {
        val loader = fixtureLoader()
        val item = loader.loadClass("release.Item")
        registry.describe(List::class.createType(listOf(KTypeProjection.invariant(item.kotlin.createType()))))
        for (name in listOf("release.Item", "release.Holder", "release.Coded")) {
            val sample = loader.loadClass(name).getMethod("sample").invoke(null)
            assertIs<SnapshotResult.Complete>(DataSnapshot.capture(registry.lift(sample)), name)
        }
        loader.close()
        return WeakReference(loader)
    }


    private fun liftOpaque(registry: DefaultDataAdapterRegistry): WeakReference<ClassLoader> {
        val loader = fixtureLoader()
        val handle = loader.loadClass("release.Handle").getMethod("sample").invoke(null)
        val expected = DataContract(
            DataType.Opaque(false),
            mapOf(DataTypePath.root to TypeMetadata(ClassName("release.Handle"), emptyList(), false)))
        registry.lift(handle, expected)
        loader.close()
        return WeakReference(loader)
    }


    private fun resolveThroughOwner(resolver: DefaultNativeTypeResolver): WeakReference<ClassLoader> {
        val loader = fixtureLoader()
        val handle = loader.loadClass("release.Handle")
        val contract = TypeMetadata(ClassName("release.Handle"), emptyList(), false).toDataContract()
        assertSame(handle.kotlin, resolver.resolve(contract, handle).tokenByPath[DataTypePath.root]?.type?.classifier)
        loader.close()
        return WeakReference(loader)
    }


    private fun describeMapWithDropped(
        registry: DefaultDataAdapterRegistry,
        kept: Class<*>
    ): Pair<WeakReference<ClassLoader>, DataContract> {
        val loader = fixtureLoader()
        val dropped = loader.loadClass("release.Item")
        val described = registry.describe(Map::class.createType(listOf(
            KTypeProjection.invariant(kept.kotlin.createType()),
            KTypeProjection.invariant(dropped.kotlin.createType()))))
        loader.close()
        return WeakReference<ClassLoader>(loader) to described
    }


    private fun fixtureLoader(): URLClassLoader =
        URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader)


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertCollectable(loader: WeakReference<ClassLoader>, label: String) {
        val deadline = System.nanoTime() + collectTimeoutNanos
        while (loader.get() != null && System.nanoTime() < deadline) {
            System.gc()
            // Garbage, in case the collector treats an explicit request as a hint
            repeat(pressureChunks) { allocationSink = ByteArray(pressureChunkBytes) }
            Thread.sleep(collectPauseMillis)
        }
        assertTrue(loader.get() == null, "$label stays reachable after garbage collection")
    }


    private companion object {
        const val collectTimeoutNanos = 30_000_000_000L
        const val collectPauseMillis = 20L
        const val pressureChunks = 32
        const val pressureChunkBytes = 1 shl 20
        const val warmupHits = 20_000
        const val measuredHits = 100_000
        const val measuredRounds = 3


        /**
         * A record with an enum member, a bean holding a list of them, a record holding arrays of them, and an
         * ordinary class with no properties (opaque) — the shapes an expression's own classes take.
         */
        fun compileFixtures(): File {
            val sources = mapOf(
                "release.Tag" to "package release; public enum Tag { RED, GREEN }",
                "release.Item" to "package release; public record Item(String name, int count, Tag tag) { " +
                        "public static Item sample() { return new Item(\"a\", 1, Tag.GREEN); } }",
                "release.Holder" to "package release; public class Holder { " +
                        "public java.util.List<Item> getItems() { return java.util.List.of(Item.sample()); } " +
                        "public static Holder sample() { return new Holder(); } }",
                "release.Coded" to "package release; public record Coded(Item[] items, Item[][] grid) { " +
                        "public static Coded sample() { return new Coded(new Item[] { Item.sample() }, " +
                        "new Item[][] { { Item.sample() }, {} }); } }",
                "release.Handle" to "package release; public class Handle { " +
                        "public static Handle sample() { return new Handle(); } }")
            val root = Files.createTempDirectory("kzen-native-release-").toFile()
            val sourceFiles = sources.map { (className, source) ->
                val file = File(root, className.replace('.', '/') + ".java")
                file.parentFile.mkdirs()
                file.writeText(source)
                file
            }
            val compiler = ToolProvider.getSystemJavaCompiler()
            check(compiler.run(null, null, null, "-d", root.absolutePath,
                *sourceFiles.map { it.absolutePath }.toTypedArray()) == 0) { "fixture compilation failed" }
            return root
        }
    }
}
