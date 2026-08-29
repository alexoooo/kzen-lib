package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class DataSnapshotTest {
    @Test
    fun capturesRecordsListingsMappingsScalarsNullAndBinaryAndDecodesWithType() {
        val value = LiteralDataValues.lift(recordOf(
            "names" to listOf("a", "b"),
            "attributes" to linkedMapOf("x" to 1),
            "binary" to byteArrayOf(1, 2),
            "note" to null),
            DataContract(DataType.Record(listOf(
                DataField(FieldId("names"), DataType.Listing(DataType.Scalar(ScalarKind.Text))),
                DataField(FieldId("attributes"), DataType.Mapping(
                    DataType.Scalar(ScalarKind.Text), DataType.Scalar(ScalarKind.Integer(32)))),
                DataField(FieldId("binary"), DataType.Scalar(ScalarKind.Binary)),
                DataField(FieldId("note"), DataType.Scalar(ScalarKind.Text, nullable = true))))))

        val snapshot = assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value)).snapshot
        assertEquals(value.type, snapshot.type)
        val decoded = snapshot.asDataValue()
        val names = decoded.access.field(decoded.root, FieldId("names"))
        assertEquals("b", decoded.access.readText(decoded.access.element(names, 1)))
        assertEquals(snapshot, DataSnapshot.of(snapshot.type, snapshot.value))
    }


    @Test
    fun factoryFreezesSourceContainersAndBinary() {
        val bytes = byteArrayOf(1, 2)
        val list = mutableListOf<tech.kzen.lib.common.exec.ExecutionValue>(BinaryExecutionValue(bytes))
        val map = linkedMapOf<String, tech.kzen.lib.common.exec.ExecutionValue>("items" to ListExecutionValue(list))
        val type = DataType.Record(listOf(DataField(
            FieldId("items"), DataType.Listing(DataType.Scalar(ScalarKind.Binary)))))
        val snapshot = DataSnapshot.of(type, MapExecutionValue(map))
        val digest = snapshot.digest()

        bytes[0] = 9
        list.clear()
        map.clear()
        assertEquals(digest, snapshot.digest())
        assertEquals(snapshot, DataSnapshot.of(type, snapshot.value))
        val exposed = snapshot.value as MapExecutionValue
        val exposedBytes = (((exposed.values.getValue("items") as ListExecutionValue)
            .values.single()) as BinaryExecutionValue).value
        exposedBytes[0] = 8
        assertEquals(digest, snapshot.digest())
    }


    @Test
    fun policiesRejectLimitsOpaqueDuplicatesAndSensitiveValues() {
        val text = LiteralDataValues.lift("abcdef")
        val limited = DataSnapshot.capture(text, SnapshotPolicy(maximumTextLength = 3))
        assertEquals(DataProblem.snapshotLimit, assertIs<SnapshotResult.Rejected>(limited).problems.single().code)

        val opaque = LiteralDataValues.lift(Any())
        assertEquals(
            DataProblem.snapshotOpaque,
            assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(opaque)).problems.single().code)

        assertEquals(
            SnapshotResult.Redacted,
            DataSnapshot.capture(text, SnapshotPolicy(), sensitive = true))
        assertEquals(
            DataProblem.snapshotRejected,
            assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(
                text,
                SnapshotPolicy(sensitive = SensitiveSnapshotPolicy.Reject),
                sensitive = true)).problems.single().code)

        val duplicateType = DataType.Record(listOf(
            DataField(FieldId("a", 0), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("a", 1), DataType.Scalar(ScalarKind.Text))))
        val hostile = duplicateRecordValue(duplicateType)
        assertEquals(
            DataProblem.snapshotDuplicateField,
            assertIs<SnapshotResult.Rejected>(DataSnapshot.capture(hostile)).problems.single().code)
    }


    @Test
    fun typedFactoryRejectsUntypedOrMismatchedTrees() {
        assertFailsWith<DataException> {
            DataSnapshot.of(DataType.Scalar(ScalarKind.Boolean), TextExecutionValue("true"))
        }
        val text = DataSnapshot.of(DataType.Scalar(ScalarKind.Text), TextExecutionValue("true"))
        assertNotEquals(
            DataSnapshot.of(DataType.Scalar(ScalarKind.Date), TextExecutionValue("true")),
            text)
    }


    @Test
    fun revisitedNativeContainerIdentityRejectsWithoutUsingToString() {
        val left = FieldId("left")
        val right = FieldId("right")
        val child = DataType.Record(emptyList())
        val record = DataType.Record(listOf(DataField(left, child), DataField(right, child)))
        val contract = DataContract(record, mapOf(
            DataTypePath(listOf(DataPathSegment.Field(left))) to TypeMetadata.any,
            DataTypePath(listOf(DataPathSegment.Field(right))) to TypeMetadata.any))
        val childContract = DataContract(child, mapOf(DataTypePath.root to TypeMetadata.any))
        val identity = Any()
        val access = object: ValueAccess {
            override fun contract(node: DataNode) = if (node.token == 0L) contract else childContract
            override fun state(node: DataNode) = DataState.Present
            override fun field(node: DataNode, field: FieldId) = DataNode(1)
            override fun native(node: DataNode) = identity
            override fun activeVariant(node: DataNode) = error("unsupported")
            override fun selected(node: DataNode) = error("unsupported")
            override fun entry(node: DataNode, key: tech.kzen.lib.common.exec.ScalarExecutionValue) = error("unsupported")
            override fun element(node: DataNode, index: Int) = error("unsupported")
            override fun size(node: DataNode) = error("unsupported")
            override fun keyAt(node: DataNode, index: Int) = error("unsupported")
            override fun scalar(node: DataNode) = error("unsupported")
            override fun readBoolean(node: DataNode) = error("unsupported")
            override fun readLong(node: DataNode) = error("unsupported")
            override fun readDouble(node: DataNode) = error("unsupported")
            override fun readText(node: DataNode) = error("unsupported")
            override fun readBinary(node: DataNode) = error("unsupported")
        }

        val result = assertIs<SnapshotResult.Rejected>(
            DataSnapshot.capture(DataValue(access, DataNode(0))))
        assertEquals(DataProblem.snapshotCycle, result.problems.single().code)
    }


    private fun duplicateRecordValue(type: DataType.Record): DataValue {
        val access = object: ValueAccess {
            override fun contract(node: DataNode): DataContract =
                if (node.token == 0L) DataContract(type) else DataContract(DataType.Scalar(ScalarKind.Text))
            override fun state(node: DataNode) = DataState.Present
            override fun field(node: DataNode, field: FieldId) = DataNode(field.occurrence.toLong() + 1)
            override fun scalar(node: DataNode) = TextExecutionValue("x")
            override fun readText(node: DataNode) = "x"
            override fun activeVariant(node: DataNode) = error("unsupported")
            override fun selected(node: DataNode) = error("unsupported")
            override fun entry(node: DataNode, key: tech.kzen.lib.common.exec.ScalarExecutionValue) = error("unsupported")
            override fun element(node: DataNode, index: Int) = error("unsupported")
            override fun size(node: DataNode) = error("unsupported")
            override fun keyAt(node: DataNode, index: Int) = error("unsupported")
            override fun readBoolean(node: DataNode) = error("unsupported")
            override fun readLong(node: DataNode) = error("unsupported")
            override fun readDouble(node: DataNode) = error("unsupported")
            override fun readBinary(node: DataNode) = error("unsupported")
            override fun native(node: DataNode) = error("unsupported")
        }
        return DataValue(access, DataNode(0))
    }
}
