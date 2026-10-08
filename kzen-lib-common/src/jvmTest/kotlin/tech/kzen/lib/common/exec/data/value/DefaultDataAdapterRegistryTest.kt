package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataVariant
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.type.toDataContract
import java.math.BigDecimal
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame


class DefaultDataAdapterRegistryTest {
    private data class Reading(val sensor: String, val value: Double)
    private data class Recursive(val name: String, var next: Recursive?)

    private class HostileOpaque {
        override fun toString(): String = error("snapshot rejection must not stringify opaque values")
    }
    private data class Overridden(val raw: Int)
    private class CapabilityValue(val text: String)

    @Suppress("ArrayInDataClass")
    private data class Coded(val codes: Array<String>)
    @Suppress("ArrayInDataClass")
    private data class Grid(val cells: Array<Array<String>>)
    @Suppress("ArrayInDataClass")
    private data class Sparse(val codes: Array<String?>)
    @Suppress("ArrayInDataClass")
    private data class Tree(val name: String, val children: Array<Tree>)


    @Test
    fun nativeDataClassRetainsIdentityAndUsesCachedStructuralPlan() {
        DefaultDataAdapterRegistry().use { registry ->
            val reading = Reading("north", 12.5)
            val value = registry.lift(reading)

            assertReading(value)
            assertSame(reading, value.access.native(value.root))
            assertEquals(
                value.access.field(value.root, FieldId("sensor")),
                value.access.field(value.root, FieldId("sensor")))
        }
    }


    @Test
    fun javaRecordUsesOnlyDeclaredRecordComponents() {
        DefaultDataAdapterRegistry().use { registry ->
            val reading = JavaReading("south", 8.25)
            val value = registry.lift(reading)

            assertEquals("south", value.access.readText(
                value.access.field(value.root, FieldId("sensor"))))
            assertEquals(8.25, value.access.readDouble(
                value.access.field(value.root, FieldId("value"))))
            assertSame(reading, value.access.native(value.root))
        }
    }


    @Test
    fun collectionsArraysAndMappingsStayStructural() {
        DefaultDataAdapterRegistry().use { registry ->
            val list = registry.lift(listOf(1, 2, 3))
            assertIs<DataType.Listing>(list.type)
            assertEquals(2L, list.access.readLong(list.access.element(list.root, 1)))

            val array = registry.lift(doubleArrayOf(1.5, 2.5))
            assertIs<DataType.Listing>(array.type)
            assertEquals(2.5, array.access.readDouble(array.access.element(array.root, 1)))

            val mapping = registry.lift(linkedMapOf("a" to 7, "b" to 9))
            assertIs<DataType.Mapping>(mapping.type)
            assertEquals(TextExecutionValue("a"), mapping.access.keyAt(mapping.root, 0))
            assertEquals(9L, mapping.access.readLong(
                mapping.access.entry(mapping.root, TextExecutionValue("b"))))
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun anArrayFieldLiftsValidatesAndNavigatesAsDescribed() {
        val codesField = DataPathSegment.Field(FieldId("codes"))
        DefaultDataAdapterRegistry().use { registry ->
            val described = registry.describe(typeOf<Coded>())
            for (codes in listOf(arrayOf("a", "b"), emptyArray())) {
                val value = registry.lift(Coded(codes))
                val node = value.access.field(value.root, codesField.id)
                assertEquals(described.child(codesField), value.access.contract(node))
                assertEquals(codes.toList(), (0 until value.access.size(node)).map { index ->
                    value.access.readText(value.access.element(node, index))
                })
                assertEquals(emptyList(), DataValueAlgebra.validate(described, value))
                assertEquals(emptyList(), DataValueAlgebra.validate(value.contract, value))
                assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value))
            }

            val javaDescribed = registry.describe(typeOf<JavaCoded>())
            val javaCoded = registry.lift(JavaCoded(arrayOf("a"), arrayOf(JavaReading("north", 1.0))))
            for (field in listOf("codes", "readings")) {
                val segment = DataPathSegment.Field(FieldId(field))
                assertEquals(
                    javaDescribed.child(segment),
                    javaCoded.access.contract(javaCoded.access.field(javaCoded.root, segment.id)))
            }
            assertEquals(emptyList(), DataValueAlgebra.validate(javaDescribed, javaCoded))
            assertIs<SnapshotResult.Complete>(DataSnapshot.capture(javaCoded))

            val array = registry.describe(typeOf<Array<String>>())
            assertEquals(array, registry.lift(arrayOf("a", "b")).contract)
            assertEquals(array, registry.lift(arrayOf("a", "b"), array).contract)
            assertEquals(array, registry.lift(emptyArray<String>()).contract)

            val texts = VariantId("texts")
            val union = DataContract(DataType.Union(listOf(
                DataVariant(texts, DataType.Listing(DataType.Scalar(ScalarKind.Text))),
                DataVariant(VariantId("number"), DataType.Scalar(ScalarKind.Integer(32))))))
            val selected = registry.lift(arrayOf("a"), union)
            assertEquals(texts, selected.access.activeVariant(selected.root))
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun nestedArraysAndArraysHoldingNullsAreTypedLevelByLevel() {
        DefaultDataAdapterRegistry().use { registry ->
            val grid = registry.lift(Grid(arrayOf(arrayOf("a"), arrayOf("b", "c"))))
            val cells = grid.access.field(grid.root, FieldId("cells"))
            assertEquals(registry.describe(typeOf<Array<Array<String>>>()), grid.access.contract(cells))
            assertEquals("c", grid.access.readText(grid.access.element(grid.access.element(cells, 1), 1)))
            assertEquals(emptyList(), DataValueAlgebra.validate(registry.describe(typeOf<Grid>()), grid))
            assertEquals(
                registry.describe(typeOf<Array<Array<String>>>()),
                registry.lift(arrayOf(emptyArray<String>())).contract)

            // The JVM lets any element be null, so a level is nullable where it holds a null
            val sparse = registry.lift(arrayOf("a", null))
            assertEquals(registry.describe(typeOf<Array<String?>>()), sparse.contract)
            assertEquals(DataState.Null, sparse.access.state(sparse.access.element(sparse.root, 1)))
            assertEquals(
                registry.describe(typeOf<Array<Array<String?>?>>()),
                registry.lift(arrayOf(arrayOf("a", null), null)).contract)
            assertEquals(
                registry.describe(typeOf<Array<Array<String>?>>()),
                registry.lift(arrayOf(arrayOf("a"), null)).contract)

            val sparseField = registry.lift(Sparse(arrayOf("a", null)))
            assertEquals(emptyList(), DataValueAlgebra.validate(registry.describe(typeOf<Sparse>()), sparseField))
            val error = assertFailsWith<DataException> {
                registry.lift(arrayOf("a", null), registry.describe(typeOf<Array<String>>()))
            }
            assertEquals(DataProblem.incompatibleType, error.problem.code)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun anArrayOfARecursiveClassLiftsValidatesAndNavigates() {
        val children = FieldId("children")
        DefaultDataAdapterRegistry().use { registry ->
            val tree = Tree("a", arrayOf(Tree("b", arrayOf(Tree("c", emptyArray()))), Tree("d", emptyArray())))
            for ((native, expected) in listOf(
                tree to registry.describe(typeOf<Tree>()),
                arrayOf(tree) to registry.describe(typeOf<Array<Tree>>()))
            ) {
                val value = registry.lift(native, expected)
                assertEquals(expected, value.contract)
                assertEquals(emptyList(), DataValueAlgebra.validate(expected, value))
                assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value))

                val access = value.access
                val root = if (native is Tree) value.root else access.element(value.root, 0)
                val b = access.element(access.field(root, children), 0)
                val c = access.element(access.field(b, children), 0)
                assertEquals("c", access.readText(access.field(c, FieldId("name"))))
                assertEquals(0, access.size(access.field(c, children)))
                assertIs<DataType.Listing>(access.contract(access.field(c, children)).structural)
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun anArrayIsTypedByItsComponentClassNotByWhatItHolds() {
        DefaultDataAdapterRegistry().use { registry ->
            val held = registry.lift(arrayOf<Any>("a", "b"))
            assertEquals(registry.describe(typeOf<Array<Any>>()), held.contract)
            assertEquals(DataType.Listing(DataType.Dynamic(false)), held.type)
            assertEquals("b", held.access.readText(held.access.element(held.root, 1)))

            // What it holds still satisfies a typed listing at run time, without narrowing its contract
            val texts = registry.describe(typeOf<Array<String>>())
            assertEquals(held.contract, registry.lift(arrayOf<Any>("a", "b"), texts).contract)
            assertFailsWith<DataException> { registry.lift(arrayOf<Any>("a", 1), texts) }

            assertEquals(
                registry.describe(typeOf<Array<CharSequence>>()),
                registry.lift(arrayOf<CharSequence>("a")).contract)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun primitiveArraysKeepTheirScalarAndOpaqueRules() {
        DefaultDataAdapterRegistry().use { registry ->
            assertEquals(registry.describe(typeOf<IntArray>()), registry.lift(intArrayOf(1, 2)).contract)
            assertEquals(registry.describe(typeOf<Array<Int>>()), registry.lift(arrayOf(1, 2)).contract)
            assertEquals(DataType.Scalar(ScalarKind.Binary), registry.lift(byteArrayOf(1)).type)
            assertIs<DataType.Opaque>(registry.lift(charArrayOf('a')).type)

            val nested = registry.lift(arrayOf(intArrayOf(1), intArrayOf(2, 3)))
            assertEquals(registry.describe(typeOf<Array<IntArray>>()), nested.contract)
            assertEquals(
                DataType.Listing(DataType.Listing(DataType.Scalar(ScalarKind.Integer(32)))),
                nested.type)
            assertEquals(3L, nested.access.readLong(nested.access.element(nested.access.element(nested.root, 1), 1)))
        }
    }


    @Test
    fun decimalLiftIsExactAndExpectedDecimalAcceptsCanonicalText() {
        val text = "12345678901234567890.1234567890123456789"
        val expected = DataContract(DataType.Scalar(ScalarKind.Decimal))
        DefaultDataAdapterRegistry().use { registry ->
            val decimal = BigDecimal(text)
            val direct = registry.lift(decimal, expected)
            assertEquals(expected.structural, direct.type)
            assertSame(decimal, direct.access.native(direct.root))
            assertEquals(TextExecutionValue(text), direct.access.scalar(direct.root))

            val guided = registry.lift(text, expected)
            assertEquals(expected.structural, guided.type)
            assertEquals(decimal, guided.access.native(guided.root))
            assertEquals(TextExecutionValue(text), guided.access.scalar(guided.root))

            val plainText = registry.lift(text)
            assertEquals(DataType.Scalar(ScalarKind.Text), plainText.type)
            assertEquals(text, plainText.access.readText(plainText.root))
        }
    }


    @Test
    fun decimalScalarTextKeepsExtremeExponentBounded() {
        val expected = DataContract(DataType.Scalar(ScalarKind.Decimal))
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift(BigDecimal("1E-1000000"), expected)
            assertEquals(TextExecutionValue("1E-1000000"), value.access.scalar(value.root))
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun expectedNativeCollectionMetadataSurvivesRuntimeShapeInference() {
        DefaultDataAdapterRegistry().use { registry ->
            val expectedList = registry.describe(typeOf<List<Reading>>())
            val list = registry.lift(listOf(Reading("north", 12.5)), expectedList)
            assertEquals(expectedList, list.contract)
            assertEquals(expectedList.nativeByPath, list.contract.nativeByPath)

            val expectedMap = registry.describe(typeOf<Map<String, Reading>>())
            val mapping = registry.lift(mapOf("reading" to Reading("south", 8.25)), expectedMap)
            assertEquals(expectedMap.nativeByPath, mapping.contract.nativeByPath)

            // An empty collection keeps the metadata its opaque elements or values require
            val expectedOpaqueList = registry.describe(typeOf<List<java.util.concurrent.atomic.AtomicInteger>>())
            assertEquals(expectedOpaqueList, registry.lift(emptyList<Any>(), expectedOpaqueList).contract)
            val expectedOpaqueMap = registry.describe(typeOf<Map<String, java.util.concurrent.atomic.AtomicInteger>>())
            assertEquals(expectedOpaqueMap, registry.lift(emptyMap<String, Any>(), expectedOpaqueMap).contract)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun expectedOpaqueStaysOpaqueAndGuidedCollectionsUseActualNullability() {
        DefaultDataAdapterRegistry().use { registry ->
            val reading = Reading("north", 12.5)
            val opaque = DataContract(
                DataType.Opaque(),
                mapOf(tech.kzen.lib.common.exec.data.type.DataTypePath.root to
                        tech.kzen.lib.common.model.structure.metadata.TypeMetadata.any))
            val opaqueValue = registry.lift(reading, opaque)
            assertEquals(opaque, opaqueValue.contract)
            assertSame(reading, opaqueValue.access.native(opaqueValue.root))

            val nullableList = tech.kzen.lib.common.model.structure.metadata.TypeMetadata(
                tech.kzen.lib.platform.ClassNames.kotlinList,
                listOf(tech.kzen.lib.common.model.structure.metadata.TypeMetadata.anyNullable),
                true).toDataContract()
            val list = registry.lift(listOf(1, 2, 3), nullableList)
            assertEquals(false, list.type.nullable)
            assertEquals(false, list.contract.nativeByPath.values.single().nullable)
        }
    }


    @Test
    fun aLiftedEmptyCollectionConformsToATypedCollectionOfItsKind() {
        DefaultDataAdapterRegistry().use { registry ->
            val texts = DataContract(DataType.Listing(DataType.Scalar(ScalarKind.Text)))
            val empty = registry.lift(emptyList<String>())
            assertSame(empty, registry.lift(empty, texts))

            val error = assertFailsWith<DataException> { registry.lift(registry.lift(listOf(1)), texts) }
            assertEquals(DataProblem.incompatibleType, error.problem.code)
            assertFailsWith<DataException> { registry.lift(registry.lift(emptyMap<String, String>()), texts) }
        }
    }


    @Test
    fun heterogeneousCollectionsWidenAndCanonicalMappingCollisionsFail() {
        DefaultDataAdapterRegistry().use { registry ->
            val heterogeneous = registry.lift(listOf(1, "two"))
            assertEquals(DataType.Dynamic(false), (heterogeneous.type as DataType.Listing).element)

            val collision = linkedMapOf<Any, String>(1 to "int", 1L to "long")
            val error = assertFailsWith<DataException> { registry.lift(collision) }
            assertEquals(DataProblem.mappingKeyCollision, error.problem.code)
        }
    }


    @Test
    fun expectedUnionProducesAnExplicitTaggedRoot() {
        val text = VariantId("text")
        val number = VariantId("number")
        val expected = DataContract(DataType.Union(listOf(
            DataVariant(text, DataType.Scalar(ScalarKind.Text)),
            DataVariant(number, DataType.Scalar(ScalarKind.Integer(32))))))
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift("hello", expected)
            assertEquals(expected, value.contract)
            assertEquals(text, value.access.activeVariant(value.root))
            val selected = value.access.selected(value.root)
            assertEquals(DataType.Scalar(ScalarKind.Text), value.access.contract(selected).structural)
            assertEquals("hello", value.access.readText(selected))
        }
    }


    @Test
    fun exactAdapterOverridesDataClassAndDuplicateExactRegistrationFails() {
        val first = textAdapter("first") { (it as Overridden).raw.toString() }
        val second = textAdapter("second") { "unused" }
        DefaultDataAdapterRegistry(listOf(ExactDataAdapter(Overridden::class, first))).use { registry ->
            val value = registry.lift(Overridden(42))
            assertEquals(DataType.Scalar(ScalarKind.Text), value.type)
            assertEquals("42", value.access.readText(value.root))
        }

        val error = assertFailsWith<DataException> {
            DefaultDataAdapterRegistry(listOf(
                ExactDataAdapter(Overridden::class, first),
                ExactDataAdapter(Overridden::class, second)))
        }
        assertEquals(DataProblem.adapterConflict, error.problem.code)
    }


    @Test
    fun capabilityFallbacksAreTriedInDeclarationOrderAfterBuiltIns() {
        val first = textAdapter("first") { "first:${(it as CapabilityValue).text}" }
        val second = textAdapter("second") { "second" }
        DefaultDataAdapterRegistry(fallbacks = listOf(
            CapabilityDataAdapter("first-capability", { it == CapabilityValue::class }, first),
            CapabilityDataAdapter("second-capability", { true }, second)
        )).use { registry ->
            val capability = registry.lift(CapabilityValue("x"))
            assertEquals("first:x", capability.access.readText(capability.root))
            val builtIn = registry.lift(3)
            assertEquals(3L, builtIn.access.readLong(builtIn.root))
        }
    }


    @Test
    fun iterableSequenceAndIteratorNeedExplicitAdapters() {
        DefaultDataAdapterRegistry().use { registry ->
            listOf<Any>(sequenceOf(1), listOf(1).iterator(), object: Iterable<Int> {
                override fun iterator(): Iterator<Int> = listOf(1).iterator()
            }).forEach { refused ->
                val error = assertFailsWith<DataException> { registry.lift(refused) }
                assertEquals(DataProblem.adapterRefused, error.problem.code)
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun adapterDescribeAndLiftDisagreementFails() {
        val mismatched = object: DataAdapter {
            override val id = DataAdapterId("mismatched")
            override fun describe(native: KType) = DataContract(DataType.Scalar(ScalarKind.Text))
            override fun lift(value: Any, expected: DataContract?) = LiteralDataValues.lift(1)
        }
        DefaultDataAdapterRegistry(listOf(
            ExactDataAdapter(Overridden::class, mismatched))).use { registry ->
            val error = assertFailsWith<DataException> { registry.lift(Overridden(1)) }
            assertEquals(DataProblem.adapterContractMismatch, error.problem.code)
            assertEquals(DataType.Scalar(ScalarKind.Text), registry.describe(typeOf<Overridden>()).structural)
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun recursiveDescriptionIsAReferenceAndSnapshotRejectsWithoutRenderingObject() {
        DefaultDataAdapterRegistry().use { registry ->
            val described = registry.describe(typeOf<Recursive>())
            val root = described.structural as DataType.Record
            assertIs<DataType.Reference>(root.fields.single { it.id.name == "next" }.type)

            val recursive = Recursive("root", null)
            recursive.next = recursive
            val value = registry.lift(recursive)
            assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(value))

            val hostile = registry.lift(HostileOpaque())
            assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(hostile))
        }
    }


    @Test
    fun fakeTypedRowUsesTheSameTraversalOperations() {
        val literal = LiteralDataValues.lift(recordOf("sensor" to "north", "value" to 12.5))
        val row = FakeTypedRowValue("north", 12.5).value()
        DefaultDataAdapterRegistry().use { registry ->
            val native = registry.lift(Reading("north", 12.5))
            listOf(literal, native, row).forEach(::assertReading)
        }
    }


    @Test
    fun postPublicationMutationSurfacesAsAReadFailure() {
        DefaultDataAdapterRegistry().use { registry ->
            val source = mutableListOf<Any>(1)
            val value = registry.lift(source)
            source[0] = "wrong"

            val error = assertFailsWith<DataAccessException> {
                value.access.element(value.root, 0)
            }
            assertEquals(DataProblem.incompatibleType, error.problem.code)
        }
    }


    @Test
    fun hostedChildStyleResultRemainsReadableAfterProducerScopeEnds() {
        fun produce(): DataValue {
            val producerRegistry = DefaultDataAdapterRegistry()
            return producerRegistry.lift(Reading("north", 12.5))
        }

        assertReading(produce())
    }


    private fun assertReading(value: DataValue) {
        val record = value.type as DataType.Record
        assertEquals(listOf("sensor", "value"), record.fields.map { it.id.name })
        assertEquals("north", value.access.readText(
            value.access.field(value.root, FieldId("sensor"))))
        assertEquals(12.5, value.access.readDouble(
            value.access.field(value.root, FieldId("value"))))
    }


    private fun textAdapter(id: String, render: (Any) -> String): DataAdapter =
        object: DataAdapter {
            override val id = DataAdapterId(id)
            override fun describe(native: KType) = DataContract(DataType.Scalar(ScalarKind.Text))
            override fun lift(value: Any, expected: DataContract?) = LiteralDataValues.lift(render(value))
        }
}


private class FakeTypedRowValue(
    private val sensor: String,
    private val reading: Double
): ValueAccess {
    companion object {
        private val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("sensor"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("value"), DataType.Scalar(ScalarKind.Floating(64))))))
    }

    fun value(): DataValue = DataValue(this, DataNode(0))

    override fun contract(node: DataNode): DataContract = when (node.token) {
        0L -> contract
        1L -> contract.child(DataPathSegment.Field(FieldId("sensor")))
        2L -> contract.child(DataPathSegment.Field(FieldId("value")))
        else -> invalid()
    }

    override fun state(node: DataNode) = DataState.Present
    override fun field(node: DataNode, field: FieldId): DataNode = when (field.name) {
        "sensor" -> DataNode(1)
        "value" -> DataNode(2)
        else -> invalid()
    }
    override fun readText(node: DataNode): String = if (node.token == 1L) sensor else invalid()
    override fun readDouble(node: DataNode): Double = if (node.token == 2L) reading else invalid()
    override fun scalar(node: DataNode): ScalarExecutionValue = when (node.token) {
        1L -> TextExecutionValue(sensor)
        2L -> tech.kzen.lib.common.exec.NumberExecutionValue(reading)
        else -> invalid()
    }

    override fun activeVariant(node: DataNode): VariantId = invalid()
    override fun selected(node: DataNode): DataNode = invalid()
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode = invalid()
    override fun element(node: DataNode, index: Int): DataNode = invalid()
    override fun size(node: DataNode): Int = invalid()
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue = invalid()
    override fun readBoolean(node: DataNode): Boolean = invalid()
    override fun readLong(node: DataNode): Long = invalid()
    override fun readBinary(node: DataNode): ByteArray = invalid()
    override fun native(node: DataNode): Any = invalid()

    private fun invalid(): Nothing = throw DataAccessException(DataProblem(
        DataProblem.invalidOperation,
        "Unsupported fake typed-row operation"))
}
