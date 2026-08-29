package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


@OptIn(ExperimentalStdlibApi::class)
class DefaultNativeTypeResolverTest {
    data class Address(val city: String)

    data class Person(
        val name: String,
        val address: Address,
        val tags: List<String>
    )

    data class RecursiveNode(
        val value: String,
        val next: RecursiveNode?
    )

    interface Named

    data class NamedRecord(val value: String): Named

    data class Location(val city: String)

    class OpaqueBox<T>

    class Adapted(val value: String)


    @Test
    fun describesPrimitivesCollectionsArraysAndNullability() {
        NativeTypeResolutionScope().use { scope ->
            assertEquals(
                DataType.Scalar(ScalarKind.Integer(32)),
                scope.resolver.describe(typeOf<Int>()).contract.structural)
            assertEquals(
                DataType.Scalar(ScalarKind.Text, nullable = true),
                scope.resolver.describe(typeOf<String?>()).contract.structural)
            assertEquals(
                DataType.Listing(DataType.Scalar(ScalarKind.Text)),
                scope.resolver.describe(typeOf<List<String>>()).contract.structural)
            assertEquals(
                DataType.Mapping(
                    DataType.Scalar(ScalarKind.Text),
                    DataType.Scalar(ScalarKind.Integer(32))),
                scope.resolver.describe(typeOf<Map<String, Int>>()).contract.structural)
            assertEquals(
                DataType.Listing(DataType.Scalar(ScalarKind.Integer(32))),
                scope.resolver.describe(typeOf<IntArray>()).contract.structural)
            assertEquals(
                DataType.Listing(DataType.Scalar(ScalarKind.Text)),
                scope.resolver.describe(typeOf<Array<String>>()).contract.structural)

            val untypedList = scope.resolver.describe(typeOf<List<*>>())
            assertEquals(DataType.Listing(DataType.Dynamic()), untypedList.contract.structural)
            assertEquals(setOf(DataTypePath.root), untypedList.tokenByPath.keys)
        }
    }


    @Test
    fun describesDataClassesJavaRecordsAndRecursion() {
        NativeTypeResolutionScope().use { scope ->
            val person = scope.resolver.describe(typeOf<Person>())
            val record = assertIs<DataType.Record>(person.contract.structural)
            assertEquals(listOf("name", "address", "tags"), record.fields.map { it.id.name })
            val addressPath = DataTypePath(listOf(DataPathSegment.Field(FieldId("address"))))
            assertEquals(typeOf<Address>(), person.tokenByPath[addressPath]?.type)
            assertNotEquals(typeOf<Person>(), person.tokenByPath[addressPath]?.type)

            val javaRecord = scope.resolver.describe(typeOf<JavaReading>())
            assertEquals(
                listOf("sensor", "value"),
                assertIs<DataType.Record>(javaRecord.contract.structural).fields.map { it.id.name })

            val recursive = scope.resolver.describe(typeOf<RecursiveNode>())
            val nextPath = DataTypePath(listOf(DataPathSegment.Field(FieldId("next"))))
            assertIs<DataType.Opaque>(recursive.contract.child(
                DataPathSegment.Field(FieldId("next"))).structural)
            assertEquals(typeOf<RecursiveNode?>(), recursive.tokenByPath[nextPath]?.type)
        }
    }


    @Test
    fun exactDescriberOverridesTheDataClassBaseline() {
        val describer = object: NativeTypeDescriber {
            override val nativeClass: KClass<*> = Adapted::class

            override fun describe(native: kotlin.reflect.KType): DataContract =
                DataContract(DataType.Record(listOf(
                    DataField(FieldId("adapted"), DataType.Scalar(ScalarKind.Text)))))
        }

        NativeTypeResolutionScope(listOf(describer)).use { scope ->
            val resolved = scope.resolver.describe(typeOf<Adapted>())
            assertEquals(
                listOf(FieldId("adapted")),
                assertIs<DataType.Record>(resolved.contract.structural).fields.map { it.id })
            assertEquals(typeOf<Adapted>(), resolved.tokenByPath[DataTypePath.root]?.type)
        }

        assertEquals(
            DataProblem.invalidResolvedContract,
            assertFailsWith<DataException> {
                NativeTypeResolutionScope(listOf(describer, describer))
            }.problem.code)
    }


    @Test
    fun resolvesNameOnlyDeclarationsInTheOwnerLoader() {
        val metadata = TypeMetadata(
            ClassName(OpaqueBox::class.qualifiedName!!),
            listOf(TypeMetadata.string),
            false)
        val contract = metadata.toDataContract()

        assertEquals(
            DataProblem.invalidResolvedContract,
            assertFailsWith<DataException> {
                ResolvedDataContract(contract, emptyMap())
            }.problem.code)
        assertIs<TypeAcceptance.Rejected>(
            DataTypeAlgebra.isAssignable(contract.structural, contract.structural))

        NativeTypeResolutionScope().use { scope ->
            val resolved = scope.resolver.resolve(contract, javaClass.classLoader)
            assertEquals(typeOf<OpaqueBox<String>>(), resolved.tokenByPath[DataTypePath.root]?.type)
        }
    }


    @Test
    fun appliesStructuralThenNativeAssignabilityIncludingGenericVariance() {
        NativeTypeResolutionScope().use { scope ->
            val expectedNamed = scope.resolver.describe(typeOf<Named>())
            val actualNamed = scope.resolver.describe(typeOf<NamedRecord>())
            assertEquals(TypeAcceptance.Accepted, scope.resolver.isAssignable(expectedNamed, actualNamed))

            assertEquals(
                TypeAcceptance.Accepted,
                scope.resolver.isAssignable(
                    scope.resolver.describe(typeOf<List<CharSequence>>()),
                    scope.resolver.describe(typeOf<List<String>>())))
            assertIs<TypeAcceptance.Rejected>(scope.resolver.isAssignable(
                scope.resolver.describe(typeOf<MutableList<CharSequence>>()),
                scope.resolver.describe(typeOf<MutableList<String>>())))
            assertIs<TypeAcceptance.Rejected>(scope.resolver.isAssignable(
                scope.resolver.describe(typeOf<List<String>>()),
                scope.resolver.describe(typeOf<List<String?>?>())))
            assertEquals(
                TypeAcceptance.Accepted,
                scope.resolver.isAssignable(
                    scope.resolver.describe(typeOf<List<List<CharSequence>>>()),
                    scope.resolver.describe(typeOf<List<List<String>>>())))

            val tokenlessActual = ResolvedDataContract(
                DataContract(DataType.Listing(DataType.Scalar(ScalarKind.Text))),
                emptyMap())
            val missing = assertIs<TypeAcceptance.Rejected>(scope.resolver.isAssignable(
                scope.resolver.describe(typeOf<List<String>>()),
                tokenlessActual))
            assertEquals(DataProblem.nativeTypeMissing, missing.problem.code)
        }
    }


    @Test
    fun nativeUnionSelectionDistinguishesSameShapedClasses() {
        NativeTypeResolutionScope().use { scope ->
            val address = scope.resolver.describe(typeOf<Address>())
            val location = scope.resolver.describe(typeOf<Location>())
            val unionType = DataType.Union(listOf(
                DataVariant(VariantId("address"), address.contract.structural),
                DataVariant(VariantId("location"), location.contract.structural)))
            val unionContract = DataContract(
                unionType,
                address.contract.nativeByPath.prefixedForTest(DataPathSegment.Variant(VariantId("address"))) +
                        location.contract.nativeByPath.prefixedForTest(
                            DataPathSegment.Variant(VariantId("location"))))
            val unionTokens =
                address.tokenByPath.prefixedForTest(DataPathSegment.Variant(VariantId("address"))) +
                        location.tokenByPath.prefixedForTest(DataPathSegment.Variant(VariantId("location")))
            val union = ResolvedDataContract(unionContract, unionTokens)

            assertEquals(
                VariantSelection.Selected(VariantId("address")),
                scope.resolver.selectVariant(union, address))
            assertEquals(
                VariantSelection.Selected(VariantId("location")),
                scope.resolver.selectVariant(union, location))
            assertEquals(
                TypeAcceptance.Accepted,
                scope.resolver.validateVariant(union, VariantId("address"), address))
        }
    }


    @Test
    fun rejectsSameNameSiblingLoadersAndAcceptsParentInterface() {
        val compiled = compileClasses(mapOf(
            "fixture.SameName" to "package fixture; public class SameName {}",
            "fixture.ChildRunnable" to
                    "package fixture; public class ChildRunnable implements java.lang.Runnable { " +
                            "public void run() {} }"))
        URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { firstLoader ->
            URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { secondLoader ->
                val firstClass = firstLoader.loadClass("fixture.SameName").kotlin
                val secondClass = secondLoader.loadClass("fixture.SameName").kotlin
                NativeTypeResolutionScope().use { scope ->
                    assertIs<TypeAcceptance.Rejected>(scope.resolver.isAssignable(
                        scope.resolver.describe(firstClass.createType()),
                        scope.resolver.describe(secondClass.createType())))

                    val child = firstLoader.loadClass("fixture.ChildRunnable").kotlin
                    assertEquals(
                        TypeAcceptance.Accepted,
                        scope.resolver.isAssignable(
                            scope.resolver.describe(typeOf<Runnable>()),
                            scope.resolver.describe(child.createType())))
                }
            }
        }
    }


    @Test
    fun releasingGenerationDropsLoaderAndRejectsFurtherUse() {
        val compiled = compileClasses(mapOf(
            "fixture.GenerationValue" to "package fixture; public class GenerationValue {}"))
        val weakLoader = resolveThenRelease(compiled)

        repeat(80) {
            if (weakLoader.get() == null) {
                return
            }
            System.gc()
            Thread.sleep(10)
        }
        assertNull(weakLoader.get(), "released resolver retained its owner class loader")
    }


    private fun resolveThenRelease(compiled: File): WeakReference<ClassLoader> {
        val loader = URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader)
        val scope = NativeTypeResolutionScope()
        val metadata = TypeMetadata(ClassName("fixture.GenerationValue"), emptyList(), false)
        scope.resolver.resolve(metadata.toDataContract(), loader)
        scope.close()
        assertEquals(
            DataProblem.nativeResolverReleased,
            assertFailsWith<DataException> {
                scope.resolver.resolve(metadata.toDataContract(), loader)
            }.problem.code)
        loader.close()
        return WeakReference(loader)
    }


    private fun compileClasses(sources: Map<String, String>): File {
        val root = Files.createTempDirectory("kzen-native-types-").toFile()
        val sourceFiles = sources.map { (className, source) ->
            val file = File(root, className.replace('.', '/') + ".java")
            file.parentFile.mkdirs()
            file.writeText(source)
            file
        }
        val compiler = ToolProvider.getSystemJavaCompiler()
        assertTrue(compiler.run(null, null, null, "-d", root.absolutePath,
            *sourceFiles.map { it.absolutePath }.toTypedArray()) == 0)
        root.deleteOnExit()
        return root
    }
}


private fun <T> Map<DataTypePath, T>.prefixedForTest(
    segment: DataPathSegment
): Map<DataTypePath, T> =
    mapKeys { (path, _) -> DataTypePath(listOf(segment) + path.segments) }
