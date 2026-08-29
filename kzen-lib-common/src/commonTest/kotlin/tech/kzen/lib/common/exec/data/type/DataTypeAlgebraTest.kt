package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class DataTypeAlgebraTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val date = DataType.Scalar(ScalarKind.Date)
    private val int8 = DataType.Scalar(ScalarKind.Integer(8))
    private val int32 = DataType.Scalar(ScalarKind.Integer(32))
    private val int64 = DataType.Scalar(ScalarKind.Integer(64))


    @Test
    fun assignabilityCoversNullabilityWidthOptionalityAndVariance() {
        assertAccepted(text, text)
        assertAccepted(DataType.Scalar(ScalarKind.Text, true), text)
        assertRejected(text, DataType.Scalar(ScalarKind.Text, true))
        assertAccepted(int64, int32)
        assertRejected(int32, int64)
        assertAccepted(DataType.Scalar(ScalarKind.Decimal), int64)
        assertAccepted(DataType.Scalar(ScalarKind.Floating(64)), int32)
        assertRejected(DataType.Scalar(ScalarKind.Floating(32)), int32)

        val narrow = record("a" to int32)
        val wide = record("a" to int32, "b" to text)
        assertAccepted(narrow, wide)
        assertRejected(wide, narrow)

        val optionalExpected = DataType.Record(listOf(DataField(FieldId("a"), int32, optional = true)))
        val optionalActual = DataType.Record(listOf(DataField(FieldId("a"), int32, optional = true)))
        assertAccepted(optionalExpected, narrow)
        assertAccepted(optionalExpected, optionalActual)
        assertRejected(narrow, optionalActual)

        assertAccepted(DataType.Listing(int64), DataType.Listing(int32))
        assertRejected(DataType.Listing(int32), DataType.Listing(int64))
        assertAccepted(
            DataType.Mapping(text, int64),
            DataType.Mapping(text, int32))
        assertRejected(
            DataType.Mapping(text, int64),
            DataType.Mapping(date, int32))

        assertAccepted(DataType.Dynamic(), wide)
        assertAccepted(DataType.Dynamic(false), int32)
        assertRejected(int32, DataType.Dynamic(false))
        assertRejected(DataType.Opaque(), DataType.Opaque())
        assertAccepted(DataType.Dynamic(), DataType.Opaque())
    }


    @Test
    fun unionAssignabilityUsesAllThreeRelations() {
        val expected = union("text" to text, "integer" to int32)
        assertAccepted(expected, text)
        assertRejected(expected, date)

        val acceptedActualUnion = union("a" to text, "b" to int8)
        val rejectedActualUnion = union("a" to text, "b" to date)
        assertAccepted(expected, acceptedActualUnion)
        assertRejected(expected, rejectedActualUnion)

        val concreteExpected = DataType.Scalar(ScalarKind.Integer(null))
        assertAccepted(concreteExpected, union("small" to int8, "large" to int64))
        assertRejected(concreteExpected, union("small" to int8, "word" to text))

        assertAccepted(
            DataType.Union(expected.variants, nullable = true),
            DataType.Scalar(ScalarKind.Text, nullable = true))
    }


    @Test
    fun variantSelectionIsUniqueOrExplicitlyFails() {
        val oneOrMany = union(
            "many" to DataType.Listing(text),
            "one" to text)
        assertEquals(
            VariantSelection.Selected(VariantId("many")),
            DataTypeAlgebra.selectVariant(oneOrMany, DataType.Listing(text)))
        assertEquals(
            VariantSelection.Selected(VariantId("one")),
            DataTypeAlgebra.selectVariant(oneOrMany, text))

        val narrow = record("a" to int32)
        val wide = record("a" to int32, "b" to text)
        val overlapping = union("narrow" to narrow, "wide" to wide)
        assertEquals(
            listOf(VariantId("narrow"), VariantId("wide")),
            assertIs<VariantSelection.Ambiguous>(
                DataTypeAlgebra.selectVariant(overlapping, wide)).candidates)
        assertEquals(
            VariantSelection.Selected(VariantId("narrow")),
            DataTypeAlgebra.selectVariant(overlapping, narrow))

        val duplicateContracts = union("first" to text, "second" to text)
        assertEquals(
            listOf(VariantId("first"), VariantId("second")),
            assertIs<VariantSelection.Ambiguous>(
                DataTypeAlgebra.selectVariant(duplicateContracts, text)).candidates)

        val noMatch = assertIs<VariantSelection.NoMatch>(
            DataTypeAlgebra.selectVariant(oneOrMany, date))
        assertEquals(DataProblem.unionVariantNoMatch, noMatch.problem.code)
        assertIs<VariantSelection.NoMatch>(
            DataTypeAlgebra.selectVariant(oneOrMany, DataType.Dynamic(false)))

        assertAccepted(oneOrMany.variants[0].type, DataType.Listing(text))
        assertEquals(
            TypeAcceptance.Accepted,
            DataTypeAlgebra.validateVariant(oneOrMany, VariantId("many"), DataType.Listing(text)))
        assertEquals(
            DataProblem.unionVariantUnknown,
            assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.validateVariant(
                oneOrMany,
                VariantId("unknown"),
                text)).problem.code)
        assertRejectedAcceptance(
            DataTypeAlgebra.validateVariant(oneOrMany, VariantId("many"), text))

        val mutableCandidates = mutableListOf(VariantId("one"))
        val ambiguity = VariantSelection.Ambiguous(mutableCandidates)
        mutableCandidates += VariantId("many")
        assertEquals(listOf(VariantId("one")), ambiguity.candidates)
    }


    @Test
    fun nullableUnionModelsRootNullWithoutASelectedVariant() {
        val nullableUnion = DataType.Union(
            listOf(DataVariant(VariantId("text"), text)),
            nullable = true)

        assertEquals(true, nullableUnion.nullable)
        assertEquals(false, nullableUnion.variants.single().type.nullable)
        assertEquals(nullableUnion, DataType.ofExecutionValue(nullableUnion.asExecutionValue()))
    }


    @Test
    fun joinObeysLawsForRepresentativeTable() {
        val types = listOf(
            text,
            int8,
            int32,
            int64,
            DataType.Scalar(ScalarKind.Integer(8, signed = false)),
            DataType.Scalar(ScalarKind.Integer(32, signed = false)),
            DataType.Scalar(ScalarKind.Integer(null, signed = false)),
            DataType.Scalar(ScalarKind.Decimal),
            DataType.Scalar(ScalarKind.Floating(32)),
            DataType.Scalar(ScalarKind.Floating(64)),
            DataType.Listing(int8),
            DataType.Listing(int32),
            DataType.Mapping(text, int8),
            DataType.Mapping(text, int32),
            record("a" to int8),
            record("a" to int32),
            record("different" to int32),
            union("one" to text),
            DataType.Dynamic(false))

        for (type in types) {
            assertEquals(type, DataTypeAlgebra.join(type, type), "idempotence for $type")
        }
        for (left in types) {
            for (right in types) {
                assertEquals(
                    DataTypeAlgebra.join(left, right),
                    DataTypeAlgebra.join(right, left),
                    "commutativity for $left and $right")
            }
        }
        for (left in types) {
            for (middle in types) {
                for (right in types) {
                    assertEquals(
                        DataTypeAlgebra.join(DataTypeAlgebra.join(left, middle), right),
                        DataTypeAlgebra.join(left, DataTypeAlgebra.join(middle, right)),
                        "associativity for $left, $middle, and $right")
                }
            }
        }

        assertEquals(
            DataType.Dynamic(false),
            DataTypeAlgebra.join(record("a" to text), record("b" to text)))
        assertEquals(
            DataType.Dynamic(false),
            DataTypeAlgebra.join(union("a" to text), union("b" to text)))
    }


    private fun record(vararg fields: Pair<String, DataType>): DataType.Record =
        DataType.Record(fields.map { DataField(FieldId(it.first), it.second) })

    private fun union(vararg variants: Pair<String, DataType>): DataType.Union =
        DataType.Union(variants.map { DataVariant(VariantId(it.first), it.second) })

    private fun assertAccepted(expected: DataType, actual: DataType) {
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(expected, actual))
    }

    private fun assertRejected(expected: DataType, actual: DataType) {
        assertRejectedAcceptance(DataTypeAlgebra.isAssignable(expected, actual))
    }

    private fun assertRejectedAcceptance(acceptance: TypeAcceptance) {
        assertIs<TypeAcceptance.Rejected>(acceptance)
    }
}
