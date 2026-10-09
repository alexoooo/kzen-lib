package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DefaultNativeTypeResolver
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * E7 item 5, the JVM half: a recursive class describes to a finite contract with named definitions, a value of
 * it navigates as deep as the data goes, snapshots stay bounded, and a same-named class in another loader has
 * the same structural reference but a different native identity.
 */
class RecursiveContractTest {
    data class Chain(val label: String, val next: Chain?)
    data class Handle(val label: String, val counter: java.util.concurrent.atomic.AtomicInteger, val next: Handle?)
    data class Tree(val name: String, val children: List<Tree>)
    data class Left(val name: String, val right: Right?)
    data class Right(val name: String, val left: Left?)
    data class ListedLeft(val name: String, val rights: List<ListedRight>)
    data class ListedRight(val name: String, val lefts: List<ListedLeft>)
    data class MappedLeft(val name: String, val rights: Map<String, MappedRight>)
    data class MappedRight(val name: String, val lefts: Map<String, MappedLeft>)
    data class Outer(val name: String, val inner: Inner?)
    data class Inner(val name: String, val outer: Outer?, val self: Inner?)
    data class Held(val name: String, val handle: AtomicInteger?, val children: List<Held>)
    data class MappedHeld(val name: String, val handle: AtomicInteger?, val children: Map<String, MappedHeld>)

    private val chainId = DefinitionId(Chain::class.qualifiedName!!)
    private val treeId = DefinitionId(Tree::class.qualifiedName!!)


    @Test
    fun selfRecursionDescribesToAFiniteContractWithOneDefinition() {
        DefaultDataAdapterRegistry().use { registry ->
            val contract = registry.describe(typeOf<Chain>())
            val root = assertIs<DataType.Record>(contract.structural)
            assertEquals(DataType.Reference(chainId, nullable = true), root.fields.single { it.id.name == "next" }.type)
            assertEquals(setOf(chainId), contract.definitions.keys)
            assertEquals(root, contract.definitions.getValue(chainId))
            assertTrue(contract.unresolvedReferences().isEmpty())
            assertEquals(contract, DefaultDataAdapterRegistry().use { it.describe(typeOf<Chain>()) }, "deterministic")

            // Navigation goes as deep as the value, one expansion per step
            val value = registry.lift(Chain("a", Chain("b", Chain("c", null))))
            assertEquals(contract, value.contract)
            var node = value.root
            val labels = mutableListOf<String>()
            while (value.access.state(node) == DataState.Present) {
                labels += value.access.readText(value.access.field(node, FieldId("label")))
                node = value.access.field(node, FieldId("next"))
            }
            assertEquals(listOf("a", "b", "c"), labels)
            val second = value.access.contract(value.access.field(value.root, FieldId("next")))
            assertEquals(root, second.structural, "a present occurrence carries the definition's shape")
            assertEquals(contract.definitions, second.definitions)
        }
    }


    @Test
    fun anExpandedReferenceKeepsItsDefinitionsOpaqueMembersMetadata() {
        // A recursive class with an opaque member (a JDK class): navigating through the reference must find the
        // member's native metadata on the expanded definition, not fail constructing the child contract
        DefaultDataAdapterRegistry().use { registry ->
            val contract = registry.describe(typeOf<Handle>())
            val handleId = DefinitionId(Handle::class.qualifiedName!!)
            assertEquals(setOf(handleId), contract.definitionNatives.keys)
            assertTrue(contract.definitionNatives.getValue(handleId).keys.any { it.toString().contains("counter") })
            val next = contract.child(DataPathSegment.Field(FieldId("next")))
            val counter = next.child(DataPathSegment.Field(FieldId("counter")))
            assertIs<DataType.Opaque>(counter.structural)
            assertTrue(counter.nativeByPath.containsKey(DataTypePath.root), "the opaque member carries its metadata")
            val deeper = next.child(DataPathSegment.Field(FieldId("next"))).child(DataPathSegment.Field(FieldId("counter")))
            assertIs<DataType.Opaque>(deeper.structural)
            assertEquals(contract, DataContract.ofExecutionValue(contract.asExecutionValue()), "definition natives round-trip")

            val value = registry.lift(Handle("a", java.util.concurrent.atomic.AtomicInteger(1), Handle("b", java.util.concurrent.atomic.AtomicInteger(2), null)))
            val inner = value.access.field(value.root, FieldId("next"))
            assertEquals("b", value.access.readText(value.access.field(inner, FieldId("label"))))
        }
    }


    @Test
    fun recursiveCollectionsAndMutualRecursionStayFinite() {
        DefaultDataAdapterRegistry().use { registry ->
            val tree = registry.describe(typeOf<Tree>())
            val children = assertIs<DataType.Listing>((tree.structural as DataType.Record).fields.single { it.id.name == "children" }.type)
            assertEquals(DataType.Reference(treeId), children.element)
            assertEquals(setOf(treeId), tree.definitions.keys)
            val lifted = registry.lift(Tree("root", listOf(Tree("leaf", emptyList()))))
            val elements = lifted.access.field(lifted.root, FieldId("children"))
            val leaf = lifted.access.element(elements, 0)
            assertEquals("leaf", lifted.access.readText(lifted.access.field(leaf, FieldId("name"))))
            assertEquals(0, lifted.access.size(lifted.access.field(leaf, FieldId("children"))))

            val left = registry.describe(typeOf<Left>())
            val leftId = DefinitionId(Left::class.qualifiedName!!)
            val rightField = (left.structural as DataType.Record).fields.single { it.id.name == "right" }
            val rightRecord = assertIs<DataType.Record>(rightField.type, "the first occurrence of Right is inline")
            assertEquals(DataType.Reference(leftId, nullable = true), rightRecord.fields.single { it.id.name == "left" }.type)
            assertEquals(setOf(leftId), left.definitions.keys, "only the class that recurs is a definition")

            val right = registry.describe(typeOf<Right>())
            assertEquals(setOf(DefinitionId(Right::class.qualifiedName!!)), right.definitions.keys)
        }
    }


    @Test
    fun mutuallyRecursiveJavaRecordsLiftAndValidate() {
        val compiled = compileClasses(mapOf(
            "fixture.Left" to "package fixture; public record Left(String name, java.util.List<Right> rights) {}",
            "fixture.Right" to "package fixture; public record Right(String name, java.util.List<Left> lefts) { " +
                    "public static Right sample() { return new Right(\"r0\", java.util.List.of(new Left(\"l0\", " +
                    "java.util.List.of(new Right(\"r1\", java.util.List.of(new Left(\"l1\", java.util.List.of()))))))); } }"))
        try {
            URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { loader ->
                val resolver = DefaultNativeTypeResolver()
                DefaultDataAdapterRegistry(nativeTypeResolver = resolver).use { registry ->
                    val rightClass = loader.loadClass("fixture.Right")
                    val leftType = loader.loadClass("fixture.Left").kotlin.createType()
                    val sample = rightClass.getMethod("sample").invoke(null)

                    val value = registry.lift(sample)
                    assertEquals(listOf("r0", "l0", "r1", "l1"), namesDown(value, "lefts", "rights"))
                    assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value))
                    val described = registry.describe(rightClass.kotlin.createType())
                    assertEquals(emptyList(), DataValueAlgebra.validate(described, value))

                    // A Left against the contract its enclosing Right gives it, which unrolls the cycle out of phase
                    // with Left's own description
                    val left = (rightClass.getMethod("lefts").invoke(sample) as List<*>).first()
                    val enclosing = described
                        .child(DataPathSegment.Field(FieldId("lefts")))
                        .child(DataPathSegment.ListingElement)
                    val nested = registry.lift(left, enclosing)
                    assertEquals(emptyList(), DataValueAlgebra.validate(enclosing, nested))
                    assertEquals(emptyList(), DataValueAlgebra.validate(registry.describe(leftType), nested))
                    assertEquals(TypeAcceptance.Accepted,
                        resolver.isAssignable(resolver.resolve(enclosing, rightClass), resolver.describe(leftType)))
                }
            }
        }
        finally {
            compiled.deleteRecursively()
        }
    }


    @Test
    fun mutualRecursionLiftsThroughAListAMapAndANullableField() {
        DefaultDataAdapterRegistry().use { registry ->
            val listed = registry.lift(ListedRight("r0", listOf(
                ListedLeft("l0", listOf(ListedRight("r1", listOf(ListedLeft("l1", emptyList()))))))))
            val mapped = registry.lift(MappedRight("r0", mapOf(
                "a" to MappedLeft("l0", mapOf("b" to MappedRight("r1", mapOf("c" to MappedLeft("l1", emptyMap()))))))))
            val nullable = registry.lift(Left("l0", Right("r0", Left("l1", Right("r1", null)))))

            assertEquals(listOf("r0", "l0", "r1", "l1"), namesDown(listed, "lefts", "rights"))
            assertEquals(listOf("r0", "l0", "r1", "l1"), namesDown(mapped, "lefts", "rights"))
            assertEquals(listOf("l0", "r0", "l1", "r1"), namesDown(nullable, "right", "left"))
            for (value in listOf(listed, mapped, nullable)) {
                assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value))
                assertEquals(emptyList(), DataValueAlgebra.validate(value.contract, value))
            }
        }
    }


    @Test
    fun fieldsDescribedFromEitherSideOfAMutualPairComposeIntoOneRecord() {
        DefaultDataAdapterRegistry().use { registry ->
            val outer = registry.lift(Outer("o0", Inner("i0", Outer("o1", null), Inner("i1", null, null))))
            val inner = registry.lift(Inner("i2", Outer("o2", Inner("i3", null, null)), null))
            val fields = listOf(FieldId("outer") to outer, FieldId("inner") to inner)

            val composed = DataContract(DataType.Record(emptyList())).withFields(fields.map { (id, value) ->
                DataField(id, value.contract.structural) to value.contract
            })
            assertEquals(composed, DataOverlay.contract(null, fields.map { (id, value) -> id to value.contract }))
            val record = DataOverlay.record(null, fields)
            assertEquals(composed, record.contract)
            assertEquals(emptyList(), DataValueAlgebra.validate(composed, record))

            val access = record.access
            val throughOuter = access.field(access.field(access.field(access.field(
                record.root, FieldId("outer")), FieldId("inner")), FieldId("outer")), FieldId("name"))
            assertEquals("o1", access.readText(throughOuter))
            val throughInner = access.field(access.field(access.field(access.field(
                record.root, FieldId("inner")), FieldId("outer")), FieldId("inner")), FieldId("name"))
            assertEquals("i3", access.readText(throughInner))
        }
    }


    @Test
    fun aRecursiveValueWithAnOpaqueMemberIsReadPastOneExpansionAsAListOrSetElement() {
        DefaultDataAdapterRegistry().use { registry ->
            val handles = List(3) { AtomicInteger(it) }
            val held = Held("a", handles[0], listOf(Held("b", handles[1], listOf(Held("c", handles[2], emptyList())))))
            val bare = Held("a", null, listOf(Held("b", null, listOf(Held("c", null, emptyList())))))

            assertReadPastOneExpansion(registry, held, bare, handles, null)
            for (expected in listOf(null, registry.describe(typeOf<List<Held>>()))) {
                assertReadPastOneExpansion(registry, listOf(held), listOf(bare), handles, expected)
            }
            for (expected in listOf(null, registry.describe(typeOf<Set<Held>>()))) {
                assertReadPastOneExpansion(registry, setOf(held), setOf(bare), handles, expected)
            }
        }
    }


    @Test
    fun aRecursiveValueWithAnOpaqueMemberIsReadPastOneExpansionAsAMapValue() {
        DefaultDataAdapterRegistry().use { registry ->
            val handles = List(3) { AtomicInteger(it) }
            val held = MappedHeld("a", handles[0], mapOf(
                "b" to MappedHeld("b", handles[1], mapOf("c" to MappedHeld("c", handles[2], emptyMap())))))
            val bare = MappedHeld("a", null, mapOf(
                "b" to MappedHeld("b", null, mapOf("c" to MappedHeld("c", null, emptyMap())))))

            assertReadPastOneExpansion(registry, held, bare, handles, null)
            for (expected in listOf(null, registry.describe(typeOf<Map<String, MappedHeld>>()))) {
                assertReadPastOneExpansion(registry, mapOf("a" to held), mapOf("a" to bare), handles, expected)
            }
        }
    }


    @Test
    fun snapshotOfACyclicValueIsBoundedAndNamed() {
        DefaultDataAdapterRegistry().use { registry ->
            val finite = registry.lift(Chain("a", Chain("b", null)))
            assertIs<SnapshotResult.Complete>(DataSnapshot.capture(finite))

            val cyclic = CyclicHolder("x")
            cyclic.next = cyclic
            val rejected = assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(registry.lift(cyclic)))
            assertTrue(rejected.problems.any { it.code == DataProblem.snapshotCycle }, rejected.problems.toString())
        }
    }


    @Test
    fun sameNameClassesInSeparateLoadersShareTheReferenceButNotTheNativeIdentity() {
        val compiled = compileClasses(mapOf(
            "fixture.Link" to "package fixture; public class Link { public String getLabel() { return \"l\"; } public Link getNext() { return null; } }"))
        try {
            URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { first ->
                URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { second ->
                    val resolver = DefaultNativeTypeResolver()
                    resolver.use {
                        val a = resolver.describe(first.loadClass("fixture.Link").kotlin.createType())
                        val b = resolver.describe(second.loadClass("fixture.Link").kotlin.createType())
                        assertEquals(a.contract, b.contract, "structural identity is the name")
                        assertEquals(setOf(DefinitionId("fixture.Link")), a.contract.definitions.keys)
                        val acceptance = resolver.isAssignable(a, b)
                        val rejected = assertIs<TypeAcceptance.Rejected>(acceptance, "native identity is the loader's")
                        assertEquals(DataProblem.nativeTypeIncompatible, rejected.problem.code)
                        assertEquals(TypeAcceptance.Accepted, resolver.isAssignable(a, a))
                    }
                }
            }
        }
        finally {
            compiled.deleteRecursively()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class CyclicHolder(val label: String) {
        var next: CyclicHolder? = null
    }


    /**
     * The `name` of each record from the root down, following the first member of [rootLink] and [partnerLink] in
     * turn (a list's first element, a map's first entry, a record field itself) until one is null or empty.
     */
    private fun namesDown(value: DataValue, rootLink: String, partnerLink: String): List<String> {
        val access = value.access
        val names = mutableListOf<String>()
        var node: DataNode? = value.root
        while (node != null) {
            names += access.readText(access.field(node, FieldId("name")))
            val linked = access.field(node, FieldId(if (names.size % 2 == 1) rootLink else partnerLink))
            node = when {
                access.state(linked) != DataState.Present -> null
                access.contract(linked).structural is DataType.Record -> linked
                access.size(linked) == 0 -> null
                access.contract(linked).structural is DataType.Mapping -> access.entry(linked, access.keyAt(linked, 0))
                else -> access.element(linked, 0)
            }
        }
        return names
    }


    /**
     * [native] (a record whose `children` hold one record per level, or a collection holding it) lifted against
     * [expected]: down every level, the value reads its `handle` as the object [handles] holds there, and the
     * contracts of the value and of each `children` node (with no value) reach the handle's native metadata past
     * one more expansion; its contract is [expected] when given, and it validates against both. [bare], the same
     * shape with no handles, snapshots completely; [native] is refused only for its opaque handle.
     */
    private fun assertReadPastOneExpansion(
        registry: DefaultDataAdapterRegistry,
        native: Any,
        bare: Any,
        handles: List<AtomicInteger>,
        expected: DataContract?
    ) {
        val handleField = DataPathSegment.Field(FieldId("handle"))
        val childrenField = DataPathSegment.Field(FieldId("children"))
        val value = registry.lift(native, expected)
        expected?.let { assertEquals(it, value.contract) }
        val access = value.access
        var node = firstMember(access, value.root)
        var contract = firstMember(value.contract)
        for (handle in handles) {
            val handleNode = access.field(checkNotNull(node), handleField.id)
            assertSame(handle, access.native(handleNode))
            assertEquals(
                AtomicInteger::class, (access as JvmNativeValueAccess).nativeType(handleNode)?.type?.classifier)
            assertOpaqueHandle(contract.child(handleField))

            val children = access.field(node, childrenField.id)
            assertOpaqueHandle(firstMember(firstMember(access.contract(children)).child(childrenField))
                .child(handleField))
            node = firstMember(access, children)
            contract = firstMember(contract.child(childrenField))
        }
        assertEquals(null, node)
        assertEquals(emptyList(), DataValueAlgebra.validate(value.contract, value))
        expected?.let { assertEquals(emptyList(), DataValueAlgebra.validate(it, value)) }

        val opaque = assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(value))
        assertEquals(listOf(DataProblem.snapshotOpaque), opaque.problems.map { it.code })
        assertIs<SnapshotResult.Complete>(DataSnapshot.capture(registry.lift(bare, expected)))
    }


    private fun assertOpaqueHandle(contract: DataContract) {
        assertIs<DataType.Opaque>(contract.structural)
        assertEquals(
            AtomicInteger::class.qualifiedName,
            contract.nativeByPath[DataTypePath.root]?.className?.get())
    }


    // A record node itself, a collection node's first member, or null for an empty collection
    private fun firstMember(access: ValueAccess, node: DataNode): DataNode? =
        when (access.contract(node).structural) {
            is DataType.Record -> node
            is DataType.Mapping -> if (access.size(node) == 0) null else access.entry(node, access.keyAt(node, 0))
            else -> if (access.size(node) == 0) null else access.element(node, 0)
        }


    private fun firstMember(contract: DataContract): DataContract =
        when (contract.expanded().structural) {
            is DataType.Record -> contract
            is DataType.Mapping -> contract.child(DataPathSegment.MappingValue)
            else -> contract.child(DataPathSegment.ListingElement)
        }


    private fun compileClasses(sources: Map<String, String>): File {
        val root = Files.createTempDirectory("kzen-recursive-").toFile()
        val compiler = ToolProvider.getSystemJavaCompiler()
        val units = sources.map { (name, source) ->
            object: SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"),
                javax.tools.JavaFileObject.Kind.SOURCE
            ) {
                override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
            }
        }
        val task = compiler.getTask(null, null, null, listOf("-d", root.absolutePath), null, units)
        check(task.call()) { "fixture compilation failed" }
        return root
    }
}
