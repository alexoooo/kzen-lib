package tech.kzen.lib.common.exec.data.value.overlay

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.recordOf
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


/** Shape-built metadata reads exactly as `ValueMetadata.of(DataOverlay.record(...))`, with its contract made once. */
class MetadataShapeTest {
    private val text = DataContract(DataType.Scalar(ScalarKind.Text))
    private val long = DataContract(DataType.Scalar(ScalarKind.Integer(64)))
    private val fileContract = MetadataContract(DataType.Record(listOf(
        DataField(FieldId("name"), text.structural),
        DataField(FieldId("size"), long.structural))))

    private val file = ValueMetadata.of(
        LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileContract.contract))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aReplacedFieldKeepsItsPositionAndAppendedFieldsFollow() {
        val fields = listOf(FieldId("size") to text, FieldId("year") to long)
        val values = listOf(LiteralDataValues.lift("large", text), LiteralDataValues.lift(2024L, long))

        val shaped = MetadataShape(file.contract, fields).metadata(file, values)

        assertEquals(listOf("name", "size", "year"), shaped.type.fields.map { it.id.name })
        assertReadsAlike(overlay(file, fields.map { it.first }, values), shaped)
        assertEquals("a.csv", readText(shaped.value, "name"))
        assertEquals("large", readText(shaped.value, "size"))
        assertEquals(2024L, shaped.access.readLong(shaped.access.field(shaped.root, FieldId("year"))))
    }


    @Test
    fun withoutBaseOnlyTheSetFieldsAreFields() {
        val values = listOf(LiteralDataValues.lift("out.csv", text))
        val shaped = MetadataShape(null, listOf(FieldId("name") to text)).metadata(null, values)

        assertReadsAlike(overlay(null, listOf(FieldId("name")), values), shaped)
        assertEquals(1, shaped.access.size(shaped.root))
        assertFailsWith<DataAccessException> { shaped.access.field(shaped.root, FieldId("size")) }
        assertFailsWith<DataAccessException> { shaped.access.readText(shaped.root) }
    }


    @Test
    fun nestedParentsNavigateThroughCompositions() {
        val parentFields = listOf(FieldId("total") to text, FieldId("parent") to file.value.payloadContract)
        val parentShape = MetadataShape(file.contract, parentFields)
        val parent = parentShape.metadata(file, listOf(LiteralDataValues.lift("12.5", text), file.value))
        val expectedParent = overlay(
            file, parentFields.map { it.first }, listOf(LiteralDataValues.lift("12.5", text), file.value))
        assertReadsAlike(expectedParent, parent)

        val chunkFields = listOf(FieldId("name") to text, FieldId("parent") to parent.value.payloadContract)
        val chunkValues = listOf(LiteralDataValues.lift("out.csv", text), parent.value)
        val chunk = MetadataShape(null, chunkFields).metadata(null, chunkValues)

        assertReadsAlike(overlay(null, chunkFields.map { it.first }, chunkValues), chunk)
        assertEquals("12.5", readText(chunk.value, "parent", "total"))
        assertEquals("a.csv", readText(chunk.value, "parent", "name"))
        assertEquals("a.csv", readText(chunk.value, "parent", "parent", "name"))
        assertEquals(3L, chunk.access.readLong(path(chunk.value, "parent", "parent", "size")))
    }


    @Test
    fun aShapeBuiltBaseIsReadThrough() {
        val first = MetadataShape(file.contract, listOf(FieldId("total") to text))
        val firstMetadata = first.metadata(file, listOf(LiteralDataValues.lift("1", text)))
        val second = MetadataShape(first.contract, listOf(FieldId("total") to long, FieldId("rank") to long))
        val values = listOf(LiteralDataValues.lift(7L, long), LiteralDataValues.lift(1L, long))

        val shaped = second.metadata(firstMetadata, values)

        assertReadsAlike(overlay(firstMetadata, listOf(FieldId("total"), FieldId("rank")), values), shaped)
        assertEquals(listOf("name", "size", "total", "rank"), shaped.type.fields.map { it.id.name })
        assertEquals(7L, shaped.access.readLong(path(shaped.value, "total")))
    }


    @Test
    fun theRootNativeIsTheOriginalRecordsUnderAnyComposition() {
        val marker = Any()
        val plain = LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileContract.contract)
        val base = ValueMetadata.of(DataValue(NativeRecord(plain, marker), plain.root))

        val once = DataOverlay.record(base.value, listOf(FieldId("total") to LiteralDataValues.lift("1", text)))
        val twice = DataOverlay.record(once, listOf(FieldId("rank") to LiteralDataValues.lift(1L, long)))
        assertSame(marker, twice.access.native(twice.root))

        val shaped = MetadataShape(base.contract, listOf(FieldId("total") to text))
            .metadata(base, listOf(LiteralDataValues.lift("1", text)))
        assertSame(marker, shaped.access.native(shaped.root))

        val unbased = MetadataShape(null, listOf(FieldId("name") to text))
            .metadata(null, listOf(LiteralDataValues.lift("a", text)))
        assertFailsWith<DataAccessException> { unbased.access.native(unbased.root) }
    }


    @Test
    fun aComposedRecordKeepsItsBasesOwnMetadata() {
        val row = LiteralDataValues.lift(recordOf("x" to "1"), DataContract(DataType.Record(listOf(
            DataField(FieldId("x"), text.structural))))).withMetadata(file)

        val composed = DataOverlay.record(row, listOf(FieldId("y") to LiteralDataValues.lift("2", text)))

        assertSame(file, composed.metadata)
        assertEquals("1", readText(composed, "x"))
        assertEquals("2", readText(composed, "y"))
    }


    @Test
    fun recordsOfOneShapeShareTheirContracts() {
        val shape = MetadataShape(file.contract, listOf(FieldId("total") to text))
        val first = shape.metadata(file, listOf(LiteralDataValues.lift("1", text)))
        val second = shape.metadata(file, listOf(LiteralDataValues.lift("2", text)))
        val payload = LiteralDataValues.lift(recordOf("x" to "1"), DataContract(DataType.Record(listOf(
            DataField(FieldId("x"), text.structural)))))

        assertSame(shape.contract, first.contract)
        assertSame(first.contract, second.contract)
        assertSame(first.value.payloadContract, second.value.payloadContract)
        assertEquals(MetadataContract.of(first.value.payloadContract), shape.contract)
        assertSame(payload.withMetadata(first).contract, payload.withMetadata(second).contract)
        assertEquals("2", readText(second.value, "total"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun contractFailuresAreRaisedByNameAtConstruction() {
        val duplicate = assertFailsWith<DataException> {
            MetadataShape(null, listOf(FieldId("a") to text, FieldId("a") to text))
        }
        assertSameFailure(duplicate) {
            DataOverlay.record(null, listOf(
                FieldId("a") to LiteralDataValues.lift("x", text),
                FieldId("a") to LiteralDataValues.lift("y", text)))
        }

        val handle = DataContract(
            DataType.Opaque(), mapOf(DataTypePath.root to TypeMetadata.any))
        val opaque = assertFailsWith<DataException> {
            MetadataShape(null, listOf(FieldId("handle") to handle))
        }
        assertSameFailure(opaque) {
            ValueMetadata.of(DataOverlay.record(null, listOf(
                FieldId("handle") to DataValue(OpaqueValue(handle, Any()), DataNode(0)))))
        }

        val nonRecord = LiteralDataValues.lift(1L, long)
        assertFailsWith<DataException> { DataOverlay.record(nonRecord, listOf(FieldId("a") to nonRecord)) }
    }


    @Test
    fun partsThatDoNotFitFailByName() {
        val shape = MetadataShape(file.contract, listOf(FieldId("total") to text))
        val other = ValueMetadata.of(LiteralDataValues.lift(recordOf("name" to "b.csv"), DataContract(
            DataType.Record(listOf(DataField(FieldId("name"), text.structural))))))
        val total = LiteralDataValues.lift("1", text)

        assertTrue(shape.fits(file, listOf(total)))
        val equalBase = ValueMetadata.of(
            LiteralDataValues.lift(recordOf("name" to "c.csv", "size" to 1L), fileContract.contract))
        assertTrue(shape.fits(equalBase, listOf(total)))

        assertFalse(shape.fits(other, listOf(total)))
        assertFalse(shape.fits(null, listOf(total)))
        assertFalse(shape.fits(file, listOf()))
        assertFalse(shape.fits(file, listOf(LiteralDataValues.lift(1L, long))))

        assertNamed("Base metadata") { shape.metadata(other, listOf(total)) }
        assertNamed("Expected 1 metadata field values") { shape.metadata(file, listOf(total, total)) }
        assertNamed("Metadata field 'FieldId(name=total") { shape.metadata(file, listOf(LiteralDataValues.lift(1L, long))) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun overlay(base: ValueMetadata?, ids: List<FieldId>, values: List<DataValue>): ValueMetadata =
        ValueMetadata.of(DataOverlay.record(base?.value, ids.zip(values)))


    private fun assertReadsAlike(expected: ValueMetadata, actual: ValueMetadata) {
        assertEquals(expected.contract, actual.contract)
        assertEquals(expected.value.payloadContract, actual.value.payloadContract)
        assertEquals(expected.access.size(expected.root), actual.access.size(actual.root))
        assertEquals(snapshot(expected.value), snapshot(actual.value))
        for (field in expected.type.fields) {
            assertEquals(
                expected.access.contract(expected.access.field(expected.root, field.id)),
                actual.access.contract(actual.access.field(actual.root, field.id)),
                field.id.name)
        }
    }


    private fun snapshot(value: DataValue): DataSnapshot =
        assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value)).snapshot


    private fun path(value: DataValue, vararg names: String): DataNode =
        names.fold(value.root) { node, name -> value.access.field(node, FieldId(name)) }


    private fun readText(value: DataValue, vararg names: String): String =
        value.access.readText(path(value, *names))


    private fun assertSameFailure(expected: DataException, block: () -> Unit) {
        val actual = assertFailsWith<DataException> { block() }
        assertEquals(expected.message, actual.message)
    }


    private fun assertNamed(prefix: String, block: () -> Unit) {
        val failure = assertFailsWith<DataException> { block() }
        assertTrue(failure.message!!.contains(prefix), failure.message)
    }


    // A record whose root carries a native, as a native-backed value's does
    private class NativeRecord(private val record: DataValue, private val marker: Any): ValueAccess by record.access {
        override fun native(node: DataNode): Any =
            if (node == record.root) marker else record.access.native(node)
    }


    private class OpaqueValue(private val contract: DataContract, private val native: Any): ValueAccess {
        override fun contract(node: DataNode) = contract
        override fun state(node: DataNode) = DataState.Present
        override fun native(node: DataNode): Any = native
        override fun activeVariant(node: DataNode) = error("opaque")
        override fun selected(node: DataNode) = error("opaque")
        override fun field(node: DataNode, field: FieldId) = error("opaque")
        override fun entry(node: DataNode, key: ScalarExecutionValue) = error("opaque")
        override fun element(node: DataNode, index: Int) = error("opaque")
        override fun size(node: DataNode) = error("opaque")
        override fun keyAt(node: DataNode, index: Int) = error("opaque")
        override fun scalar(node: DataNode) = error("opaque")
        override fun readBoolean(node: DataNode) = error("opaque")
        override fun readLong(node: DataNode) = error("opaque")
        override fun readDouble(node: DataNode) = error("opaque")
        override fun readText(node: DataNode) = error("opaque")
        override fun readBinary(node: DataNode) = error("opaque")
    }
}
