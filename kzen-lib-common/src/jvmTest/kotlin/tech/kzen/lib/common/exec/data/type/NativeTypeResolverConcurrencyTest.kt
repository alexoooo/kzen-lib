package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * One registry (and its native type resolver) is used from concurrent Worker threads (kzen-auto's
 * `JobDataValues.adapters` is process-wide), so its caches must stay consistent under concurrent misses: every
 * caller of one type gets the one cached result, nothing is lost or corrupted, and a miss computes without
 * holding a lock that a recursive or concurrent description would wait on.
 */
class NativeTypeResolverConcurrencyTest {
    //-----------------------------------------------------------------------------------------------------------------
    class Gated(val label: String)


    /** Describes [Gated] only once [arrivals] callers are describing it at the same time. */
    private class GatedDescriber(arrivals: Int): NativeTypeDescriber {
        val arrived = CountDownLatch(arrivals)

        override val nativeClass: KClass<*> = Gated::class

        override fun describe(native: KType): DataContract {
            arrived.countDown()
            check(arrived.await(gateSeconds, TimeUnit.SECONDS)) { "Concurrent describers did not all arrive" }
            return DataContract(DataType.Record(listOf(DataField(FieldId("label"), DataType.Scalar(ScalarKind.Text)))))
        }
    }


    @Test
    fun concurrentMissesOfOneTypeShareOneDescription() {
        val describer = GatedDescriber(racers)
        DefaultDataAdapterRegistry(nativeTypeResolver = DefaultNativeTypeResolver(listOf(describer))).use { registry ->
            val results = inParallel(racers) { registry.describe(typeOf<Gated>()) }

            assertSame(results[0], results[1], "both concurrent misses return the one cached description")
            assertSame(results[0], registry.describe(typeOf<Gated>()), "and it stays cached")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Loaded(val label: String, val count: Int)


    /**
     * Loads [Loaded] (from its parent) only once [arrivals] callers are loading it at the same time, and defines its
     * own copy of [Gated] as an owner to resolve through it.
     */
    private class GatedLoader(arrivals: Int): ClassLoader(NativeTypeResolverConcurrencyTest::class.java.classLoader) {
        companion object {
            init {
                // Otherwise the JVM serializes loads through this loader, and the second caller never arrives
                check(registerAsParallelCapable())
            }
        }

        val arrived = CountDownLatch(arrivals)

        val owner: Class<*> = Gated::class.java.let { gated ->
            val bytes = gated.getResourceAsStream(gated.name.substringAfterLast('.') + ".class")
                ?.use { it.readBytes() }
                ?: error("No class file for $gated")
            defineClass(gated.name, bytes, 0, bytes.size)
        }

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name == Loaded::class.java.name) {
                arrived.countDown()
                check(arrived.await(gateSeconds, TimeUnit.SECONDS)) { "Concurrent loads did not all arrive" }
            }
            return super.loadClass(name, resolve)
        }
    }


    @Test
    fun concurrentResolutionsInOneNewLoaderShareOneResult() {
        DefaultNativeTypeResolver().use { resolver ->
            val contract = resolver.describe(typeOf<Loaded>()).contract
            val owner = GatedLoader(racers).owner

            val results = inParallel(racers) { resolver.resolve(contract, owner) }

            assertSame(results[0], results[1], "both concurrent misses return the one cached resolution")
            assertSame(results[0], resolver.resolve(contract, owner), "and it stays cached")
            assertEquals(Loaded::class, results[0].tokenByPath.getValue(DataTypePath.root).type.classifier)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun manyFreshTypesDescribeResolveAndLiftConcurrentlyAsTheyDoAlone() {
        val compiled = compileFixtures()
        try {
            val alone = DefaultNativeTypeResolver()
            val expected = DefaultDataAdapterRegistry(nativeTypeResolver = alone).use { registry ->
                URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { loader ->
                    fixtureNames.associateWith { name -> outcome(registry, alone, loader, name) }
                }
            }

            val resolver = DefaultNativeTypeResolver()
            DefaultDataAdapterRegistry(nativeTypeResolver = resolver).use { registry ->
                repeat(rounds) {
                    val loaders = List(loadersPerRound) {
                        URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader)
                    }
                    try {
                        stressRound(registry, resolver, loaders, expected)
                    }
                    finally {
                        loaders.forEach { it.close() }
                    }
                }
            }
        }
        finally {
            compiled.deleteRecursively()
        }
    }


    private class Outcome(
        val described: DataContract,
        val resolved: ResolvedDataContract,
        val snapshot: ExecutionValue?)


    private fun outcome(
        registry: DefaultDataAdapterRegistry,
        resolver: DefaultNativeTypeResolver,
        loader: ClassLoader,
        name: String
    ): Outcome {
        val type = loader.loadClass(name)
        val described = registry.describe(type.kotlin.createType())
        val resolved = resolver.resolve(described, type)
        val sample = type.methods.firstOrNull { it.name == "sample" }
            ?: return Outcome(described, resolved, null)
        val value = registry.lift(sample.invoke(null))
        val snapshot = when (val result = DataSnapshot.capture(value)) {
            is SnapshotResult.Complete -> result.snapshot.value
            else -> fail("$name did not snapshot: $result")
        }
        return Outcome(described, resolved, snapshot)
    }


    private fun stressRound(
        registry: DefaultDataAdapterRegistry,
        resolver: DefaultNativeTypeResolver,
        loaders: List<ClassLoader>,
        expected: Map<String, Outcome>
    ) {
        val tasks = loaders.flatMap { loader -> fixtureNames.map { loader to it } }
        val seen = ConcurrentHashMap<Pair<Int, String>, MutableSet<Any>>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads) { runnable ->
            Thread(runnable, "native-type-stress").also { it.isDaemon = true }
        }
        try {
            repeat(threads) { thread ->
                pool.submit {
                    start.await()
                    // Each thread starts elsewhere, so distinct misses insert concurrently and the same ones collide
                    for (step in tasks.indices) {
                        val (loader, name) = tasks[(step + thread * tasks.size / threads) % tasks.size]
                        try {
                            val outcome = outcome(registry, resolver, loader, name)
                            val key = loaders.indexOf(loader) to name
                            seen.computeIfAbsent(key) { Collections.synchronizedSet(identitySet()) }.let {
                                it += outcome.described
                                it += outcome.resolved
                            }
                            check(outcome.described == expected.getValue(name).described) {
                                "$name described differently: ${outcome.described}"
                            }
                            check(outcome.snapshot == expected.getValue(name).snapshot) {
                                "$name lifted differently: ${outcome.snapshot}"
                            }
                            check(outcome.resolved.contract == expected.getValue(name).resolved.contract) {
                                "$name resolved differently: ${outcome.resolved.contract}"
                            }
                            val rootClass = outcome.resolved.tokenByPath[DataTypePath.root]?.type?.classifier
                            check((rootClass as? KClass<*>)?.java?.classLoader === loader) {
                                "$name resolved outside its loader: $rootClass"
                            }
                        }
                        catch (e: Throwable) {
                            failures += e
                        }
                    }
                }
            }
            start.countDown()
            pool.shutdown()
            if (!pool.awaitTermination(stressSeconds, TimeUnit.SECONDS)) {
                fail("Concurrent resolution did not finish within $stressSeconds s (a corrupted map can loop)")
            }
        }
        finally {
            pool.shutdownNow()
        }

        failures.firstOrNull()?.let { first ->
            throw AssertionError("${failures.size} concurrent failure(s); first: $first", first)
        }
        val divergent = seen.filterValues { it.size != 2 }
        assertTrue(divergent.isEmpty(),
            "One cached description and resolution per type and loader; diverged for ${divergent.size} of " +
                    "${seen.size}: ${divergent.keys.take(5)}")
        for ((key, results) in seen) {
            val loader = loaders[key.first]
            val type = loader.loadClass(key.second)
            val described = registry.describe(type.kotlin.createType())
            assertTrue(results.any { it === described }, "$key still cached after the round")
            assertTrue(results.any { it === resolver.resolve(described, type) }, "$key resolution still cached")
        }
        assertEquals(loaders.size * fixtureNames.size, seen.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun <T> inParallel(count: Int, action: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(count) { runnable ->
            Thread(runnable, "native-type-gate").also { it.isDaemon = true }
        }
        try {
            val futures = List(count) { pool.submit<T> { action() } }
            return futures.map { it.get(gateSeconds * 2, TimeUnit.SECONDS) }
        }
        finally {
            pool.shutdownNow()
        }
    }


    private fun identitySet(): MutableSet<Any> =
        Collections.newSetFromMap(IdentityHashMap())


    private companion object {
        const val racers = 2
        const val gateSeconds = 20L
        const val stressSeconds = 120L
        const val threads = 8
        const val rounds = 3
        const val loadersPerRound = 12
        const val levels = 12

        val fixtureNames: List<String> =
            listOf("fixture.Color", "fixture.Node", "fixture.Left", "fixture.Right") +
                    (0 until levels).map { "fixture.Level$it" } +
                    (0 until levels).map { "fixture.Bean$it" }


        /**
         * Records (one self-recursive, two mutually recursive, a chain of nested levels with collections, maps
         * and an enum) and beans over them, compiled once and loaded per round into fresh loaders, so every round
         * meets classes the registry has never described.
         */
        fun compileFixtures(): File {
            val sources = mutableMapOf(
                "fixture.Color" to "package fixture; public enum Color { RED, GREEN; " +
                        "public static Color sample() { return GREEN; } }",
                "fixture.Node" to "package fixture; public record Node(String name, java.util.List<Node> children) { " +
                        "public static Node sample() { return new Node(\"root\", " +
                        "java.util.List.of(new Node(\"leaf\", new java.util.ArrayList<>()))); } }",
                "fixture.Left" to "package fixture; public record Left(String name, java.util.List<Right> rights) {}",
                "fixture.Right" to "package fixture; public record Right(String name, java.util.List<Left> lefts) { " +
                        "public static Right sample() { return new Right(\"r\", java.util.List.of(new Left(\"l\", " +
                        "java.util.List.of(new Right(\"leaf\", new java.util.ArrayList<>()))))); } }",
                "fixture.Level0" to "package fixture; public record Level0(int id, Color color, Node tree) { " +
                        "public static Level0 sample() { return new Level0(0, Color.RED, Node.sample()); } }")
            for (level in 1 until levels) {
                val previous = "Level${level - 1}"
                sources["fixture.Level$level"] = "package fixture; public record Level$level(" +
                        "long id, String label, $previous previous, java.util.List<Node> nodes, " +
                        "java.util.Map<String, Integer> tags) { " +
                        "public static Level$level sample() { return new Level$level($level, \"l$level\", " +
                        "$previous.sample(), java.util.List.of(Node.sample()), java.util.Map.of(\"t\", $level)); } }"
            }
            for (level in 0 until levels) {
                sources["fixture.Bean$level"] = "package fixture; public class Bean$level { " +
                        "private final Level$level level = Level$level.sample(); " +
                        "public int getValue() { return $level; } " +
                        "public String getName() { return \"b$level\"; } " +
                        "public Level$level getLevel() { return level; } " +
                        "public static Bean$level sample() { return new Bean$level(); } }"
            }

            val root = Files.createTempDirectory("kzen-native-stress-").toFile()
            val compiler = ToolProvider.getSystemJavaCompiler()
            val units = sources.map { (name, source) ->
                object: SimpleJavaFileObject(
                    URI.create("string:///" + name.replace('.', '/') + ".java"),
                    JavaFileObject.Kind.SOURCE
                ) {
                    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
                }
            }
            val task = compiler.getTask(null, null, null, listOf("-d", root.absolutePath), null, units)
            check(task.call()) { "fixture compilation failed" }
            return root
        }
    }
}
