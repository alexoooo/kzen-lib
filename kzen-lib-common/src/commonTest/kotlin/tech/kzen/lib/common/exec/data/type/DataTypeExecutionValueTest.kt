package tech.kzen.lib.common.exec.data.type

import kotlin.test.Test
import kotlin.test.assertEquals


class DataTypeExecutionValueTest {
    @Test
    fun everyTypeAndScalarCaseRoundTrips() {
        val scalarKinds = listOf(
            ScalarKind.Boolean,
            ScalarKind.Integer(),
            ScalarKind.Integer(8, signed = false),
            ScalarKind.Integer(16),
            ScalarKind.Integer(32),
            ScalarKind.Integer(64),
            ScalarKind.Decimal,
            ScalarKind.Floating(32),
            ScalarKind.Floating(64),
            ScalarKind.Text,
            ScalarKind.Binary,
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid)

        val scalarTypes = scalarKinds.flatMap { kind ->
            listOf(DataType.Scalar(kind), DataType.Scalar(kind, nullable = true))
        }
        val text = DataType.Scalar(ScalarKind.Text)
        val compositeTypes = listOf(
            DataType.Record(emptyList()),
            DataType.Record(listOf(
                DataField(FieldId("a"), text),
                DataField(FieldId("a", 1), DataType.Scalar(ScalarKind.Integer(32)), optional = true)),
                nullable = true),
            DataType.Mapping(text, DataType.Listing(text), nullable = true),
            DataType.Mapping(DataType.Dynamic(false), text),
            DataType.Listing(text),
            DataType.Union(listOf(
                DataVariant(VariantId("one"), text),
                DataVariant(VariantId("many"), DataType.Listing(text))),
                nullable = true),
            DataType.Opaque(),
            DataType.Opaque(nullable = true),
            DataType.Dynamic(false),
            DataType.Dynamic())

        for (type in scalarTypes + compositeTypes) {
            assertEquals(type, DataType.ofExecutionValue(type.asExecutionValue()), "round-trip for $type")
            assertEquals(type.asExecutionValue().digest(), type.asExecutionValue().digest())
        }
    }


    @Test
    fun orderedRecordAndUnionIdentityIsCanonical() {
        val text = DataType.Scalar(ScalarKind.Text)
        val integer = DataType.Scalar(ScalarKind.Integer(32))
        val recordOne = DataType.Record(listOf(
            DataField(FieldId("a"), text),
            DataField(FieldId("b"), integer)))
        val recordTwo = DataType.Record(listOf(
            DataField(FieldId("b"), integer),
            DataField(FieldId("a"), text)))
        val unionOne = DataType.Union(listOf(
            DataVariant(VariantId("a"), text),
            DataVariant(VariantId("b"), integer)))
        val unionTwo = DataType.Union(listOf(
            DataVariant(VariantId("b"), integer),
            DataVariant(VariantId("a"), text)))

        kotlin.test.assertNotEquals(recordOne, recordTwo)
        kotlin.test.assertNotEquals(recordOne.asExecutionValue().digest(), recordTwo.asExecutionValue().digest())
        kotlin.test.assertNotEquals(unionOne, unionTwo)
        kotlin.test.assertNotEquals(unionOne.asExecutionValue().digest(), unionTwo.asExecutionValue().digest())
    }
}
