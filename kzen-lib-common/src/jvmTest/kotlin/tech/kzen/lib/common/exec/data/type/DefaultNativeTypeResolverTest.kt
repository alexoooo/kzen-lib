package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
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

    // Inner recurs in Outer's description through itself, so Outer's unrolling of Inner is not Inner's own
    data class Outer(val inner: Inner?)
    data class Inner(val outer: Outer?, val self: Inner?)

    // Each recurs in the other's own description
    data class Ping(val pong: Pong?, val ping: Ping?)
    data class Pong(val ping: Ping?, val pong: Pong?)

    data class Holder(val outer: Outer, val inner: Inner)

    data class Box(val items: List<String>)
    data class Grouped(val groups: List<List<String>>)
    data class Indexed(val byKey: Map<String, List<Int>>)
    data class Maybe(val items: List<String>?)
    data class Tokens(val tokens: Set<Token>)
    open class Token
    class SubToken: Token()
    class OtherToken
    @Suppress("ArrayInDataClass")
    data class Coded(val codes: Array<String>)


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
            assertIs<DataType.Reference>((recursive.contract.structural as DataType.Record)
                .fields.single { it.id.name == "next" }.type)
            assertIs<DataType.Record>(recursive.contract.child(
                DataPathSegment.Field(FieldId("next"))).structural, "the reference expands on navigation")
            assertEquals(typeOf<RecursiveNode?>(), recursive.tokenByPath[nextPath]?.type)
        }
    }


    @Test
    fun aRecursiveClassHasOneDefinitionWhicheverTypeIsDescribedFirst() {
        val outerId = DefinitionId(Outer::class.qualifiedName!!)
        val innerId = DefinitionId(Inner::class.qualifiedName!!)
        val outerFirst = NativeTypeResolutionScope().use { scope ->
            val outer = scope.resolver.describe(typeOf<Outer>()).contract
            outer to scope.resolver.describe(typeOf<Inner>()).contract
        }
        val innerFirst = NativeTypeResolutionScope().use { scope ->
            val inner = scope.resolver.describe(typeOf<Inner>()).contract
            scope.resolver.describe(typeOf<Outer>()).contract to inner
        }
        assertEquals(outerFirst, innerFirst)

        val (outer, inner) = outerFirst
        assertEquals(
            DataType.Record(listOf(DataField(FieldId("inner"), DataType.Record(listOf(
                DataField(FieldId("outer"), DataType.Reference(outerId, nullable = true)),
                DataField(FieldId("self"), DataType.Reference(innerId, nullable = true))), nullable = true)))),
            outer.structural,
            "the root unrolls each class once, up to its first recurrence")
        assertEquals(setOf(outerId, innerId), outer.definitions.keys)
        assertEquals(setOf(innerId), inner.definitions.keys, "Outer does not recur in Inner's description")
        assertEquals(inner.definitions.getValue(innerId), outer.definitions.getValue(innerId))
        assertEquals(inner.definitionNatives.getValue(innerId), outer.definitionNatives.getValue(innerId))
        assertEquals(outer.structural, outer.definitions.getValue(outerId), "a definition is its class's own description")
        assertEquals(inner.structural, inner.definitions.getValue(innerId))
        assertTrue(outer.unresolvedReferences().isEmpty())

        NativeTypeResolutionScope().use { scope ->
            val ping = scope.resolver.describe(typeOf<Ping>()).contract
            val pong = scope.resolver.describe(typeOf<Pong>()).contract
            assertEquals(2, ping.definitions.size)
            assertEquals(ping.definitions, pong.definitions)
            assertEquals(ping.definitionNatives, pong.definitionNatives)

            val holder = scope.resolver.describe(typeOf<Holder>()).contract
            assertEquals(outer.definitions + inner.definitions, holder.definitions, "a non-recursive root carries them too")
            assertEquals(outer.definitionNatives + inner.definitionNatives, holder.definitionNatives)
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
            val resolved = scope.resolver.resolve(contract, javaClass)
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
    fun aRunTimeValueIsCheckedThroughItsGenericCollectionsByTheirContents() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val accepted = listOf(
                    typeOf<Box>() to Box(listOf("a", "b")),
                    typeOf<Grouped>() to Grouped(listOf(listOf("a"), listOf("b", "c"))),
                    typeOf<Indexed>() to Indexed(mapOf("a" to listOf(1, 2))),
                    typeOf<Maybe>() to Maybe(listOf("a")),
                    typeOf<Tokens>() to Tokens(setOf(Token())),
                    typeOf<List<String>>() to listOf("a"),
                    typeOf<Map<String, List<Int>>>() to mapOf("a" to listOf(1)),
                    typeOf<List<Token>>() to listOf(Token()))
                for ((type, value) in accepted) {
                    assertEquals(
                        TypeAcceptance.Accepted,
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type")
                }

                // The JVM erases a run-time list's type argument, so what it holds decides; a declared type keeps it
                val heldStrings: List<Any> = listOf("a", "b")
                assertEquals(
                    TypeAcceptance.Accepted,
                    resolver.isAssignable(resolver.describe(typeOf<List<String>>()), registry.lift(heldStrings)))
                assertIs<TypeAcceptance.Rejected>(resolver.isAssignable(
                    resolver.describe(typeOf<List<String>>()), resolver.describe(typeOf<List<Any>>())))

                val producer = resolver.describe(typeOf<MutableList<out Token>>())
                assertEquals(
                    TypeAcceptance.Accepted,
                    resolver.isAssignable(producer, registry.lift(mutableListOf(SubToken()), producer.contract)))
            }
        }
    }


    @Test
    fun emptyCollectionsAndNullsHoldNothingToCheck() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val accepted = listOf(
                    typeOf<Box>() to Box(emptyList()),
                    typeOf<Grouped>() to Grouped(emptyList()),
                    typeOf<Grouped>() to Grouped(listOf(emptyList())),
                    typeOf<Indexed>() to Indexed(emptyMap()),
                    typeOf<Indexed>() to Indexed(mapOf("a" to emptyList())),
                    typeOf<Maybe>() to Maybe(null),
                    typeOf<Tokens>() to Tokens(emptySet()))
                for ((type, value) in accepted) {
                    assertEquals(
                        TypeAcceptance.Accepted,
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type $value")
                }

                val nullableList = resolver.describe(typeOf<List<Token>?>())
                assertEquals(
                    TypeAcceptance.Accepted,
                    resolver.isAssignable(nullableList, registry.lift(null, nullableList.contract)))

                // A Java record component carries no nullability, so its contract is non-null while the value holds null
                val missingSensor = assertIs<TypeAcceptance.Rejected>(resolver.isAssignable(
                    resolver.describe(typeOf<JavaReading>()), registry.lift(JavaReading(null, 1.0))))
                assertEquals(DataProblem.invalidState, missingSensor.problem.code)
            }
        }
    }


    @Test
    fun anEmptyCollectionLiftedUntypedIsAcceptedByAnyCollectionOfItsKind() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val accepted = listOf(
                    typeOf<List<String>>() to emptyList<String>(),
                    typeOf<List<String>>() to ArrayList<String>(),
                    typeOf<List<String>?>() to emptyList<String>(),
                    typeOf<Set<Token>>() to emptySet<Token>(),
                    typeOf<Set<Token>>() to HashSet<Token>(),
                    typeOf<Map<String, List<Int>>>() to emptyMap<String, List<Int>>(),
                    typeOf<Map<String, List<Int>>>() to LinkedHashMap<String, List<Int>>(),
                    typeOf<Map<String, List<Int>>>() to mapOf("a" to emptyList(), "b" to listOf(1)),
                    typeOf<List<List<String>>>() to listOf(emptyList(), listOf("a")))
                for ((type, value) in accepted) {
                    assertEquals(
                        TypeAcceptance.Accepted,
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type $value")
                }

                val rejected = listOf(
                    typeOf<List<String>>() to emptySet<String>(),
                    typeOf<Map<String, List<Int>>>() to emptyList<String>(),
                    typeOf<List<String>>() to emptyMap<String, String>(),
                    typeOf<Map<String, List<Int>>>() to mapOf("a" to emptyList(), "b" to listOf("x")),
                    typeOf<List<List<String>>>() to listOf(emptyList(), listOf(1)))
                for ((type, value) in rejected) {
                    assertIs<TypeAcceptance.Rejected>(
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type $value")
                }

                // Between contracts, an unknown element is still not a typed one
                val strings = resolver.describe(typeOf<List<String>>())
                assertIs<TypeAcceptance.Rejected>(
                    DataTypeAlgebra.isAssignable(strings.contract, registry.lift(emptyList<String>()).contract))
                assertIs<TypeAcceptance.Rejected>(
                    resolver.isAssignable(strings, resolver.describe(typeOf<List<Any?>>())))
            }
        }
    }


    @Test
    fun aMismatchedElementIsStillRejected() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val rejected = listOf(
                    typeOf<List<String>>() to listOf(1, 2),
                    typeOf<List<String>>() to listOf("a", 1),
                    typeOf<List<Token>>() to listOf(OtherToken()),
                    typeOf<Set<Token>>() to setOf(OtherToken()),
                    typeOf<Map<String, List<Int>>>() to mapOf("a" to listOf("x")),
                    typeOf<Map<String, List<Token>>>() to mapOf("a" to listOf(OtherToken())),
                    typeOf<List<List<String>>>() to listOf(listOf(1)))
                for ((type, value) in rejected) {
                    assertIs<TypeAcceptance.Rejected>(
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type $value")
                }

                @Suppress("UNCHECKED_CAST")
                val polluted = Box(listOf<Any>("a", 1) as List<String>)
                assertIs<TypeAcceptance.Rejected>(
                    resolver.isAssignable(resolver.describe(typeOf<Box>()), registry.lift(polluted)))
            }
        }
    }


    @Test
    fun aRunTimeArrayIsCheckedByItsComponentClassAndWhatItHolds() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val accepted = listOf(
                    typeOf<Coded>() to Coded(arrayOf("a", "b")),
                    typeOf<Coded>() to Coded(emptyArray()),
                    typeOf<Array<String>>() to arrayOf("a"),
                    typeOf<Array<String?>>() to arrayOf("a", null),
                    typeOf<Array<Array<String>>>() to arrayOf(arrayOf("a"), emptyArray()),
                    typeOf<Array<Token>>() to arrayOf(SubToken()))
                for ((type, value) in accepted) {
                    assertEquals(
                        TypeAcceptance.Accepted,
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type")
                }

                // Unlike a collection's type argument, an array's component class is reified: Object[] is no String[]
                val rejected = listOf(
                    typeOf<Array<String>>() to arrayOf<Any>("a"),
                    typeOf<Array<String>>() to arrayOf("a", null),
                    typeOf<Array<Int>>() to arrayOf("a"),
                    typeOf<Array<Token>>() to arrayOf(OtherToken()))
                for ((type, value) in rejected) {
                    assertIs<TypeAcceptance.Rejected>(
                        resolver.isAssignable(resolver.describe(type), registry.lift(value)),
                        "$type")
                }
            }
        }
    }


    @Test
    fun nativeUnionSelectionAmongGenericCollectionsFollowsTheirContents() {
        DefaultDataAdapterRegistry().use { registry ->
            NativeTypeResolutionScope().use { scope ->
                val resolver = scope.resolver
                val texts = resolver.describe(typeOf<List<String>>())
                val tokens = resolver.describe(typeOf<List<Token>>())
                val unionType = DataType.Union(listOf(
                    DataVariant(VariantId("texts"), texts.contract.structural),
                    DataVariant(VariantId("tokens"), tokens.contract.structural)))
                val union = ResolvedDataContract(
                    DataContract(
                        unionType,
                        texts.contract.nativeByPath.prefixedForTest(DataPathSegment.Variant(VariantId("texts"))) +
                                tokens.contract.nativeByPath.prefixedForTest(
                                    DataPathSegment.Variant(VariantId("tokens")))),
                    texts.tokenByPath.prefixedForTest(DataPathSegment.Variant(VariantId("texts"))) +
                            tokens.tokenByPath.prefixedForTest(DataPathSegment.Variant(VariantId("tokens"))))

                val heldTokens = resolver.describe(registry.lift(listOf(Token()), tokens.contract))
                assertEquals(VariantSelection.Selected(VariantId("tokens")), resolver.selectVariant(union, heldTokens))
                assertEquals(
                    TypeAcceptance.Accepted,
                    resolver.validateVariant(union, VariantId("tokens"), heldTokens))
                assertIs<TypeAcceptance.Rejected>(resolver.validateVariant(union, VariantId("texts"), heldTokens))
            }
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
        try {
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
        finally {
            compiled.deleteRecursively()
        }
    }


    @Test
    fun releasingGenerationDropsLoaderAndRejectsFurtherUse() {
        val compiled = compileClasses(mapOf(
            "fixture.GenerationValue" to "package fixture; public class GenerationValue {}"))
        try {
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
        finally {
            compiled.deleteRecursively()
        }
    }


    private fun resolveThenRelease(compiled: File): WeakReference<ClassLoader> {
        val loader = URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader)
        val owner = loader.loadClass("fixture.GenerationValue")
        val scope = NativeTypeResolutionScope()
        val metadata = TypeMetadata(ClassName("fixture.GenerationValue"), emptyList(), false)
        scope.resolver.resolve(metadata.toDataContract(), owner)
        scope.close()
        assertEquals(
            DataProblem.nativeResolverReleased,
            assertFailsWith<DataException> {
                scope.resolver.resolve(metadata.toDataContract(), owner)
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
        return root
    }
}


private fun <T> Map<DataTypePath, T>.prefixedForTest(
    segment: DataPathSegment
): Map<DataTypePath, T> =
    mapKeys { (path, _) -> DataTypePath(listOf(segment) + path.segments) }
