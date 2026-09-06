package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DefaultNativeTypeResolver
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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


    //-----------------------------------------------------------------------------------------------------------------
    class CyclicHolder(val label: String) {
        var next: CyclicHolder? = null
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
