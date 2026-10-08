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


    //-----------------------------------------------------------------------------------------------------------------
    private val rightId = DefinitionId("fixture.Right")
    private val leftId = DefinitionId("fixture.Left")
    private val otherField = DataPathSegment.Field(FieldId("other"))

    private class Wrap(val name: String, val segments: List<DataPathSegment>, val around: (DataType) -> DataType)

    private val wraps = listOf(
        Wrap("list", listOf(DataPathSegment.ListingElement)) { DataType.Listing(it) },
        Wrap("map", listOf(DataPathSegment.MappingValue)) { DataType.Mapping(text, it) },
        Wrap("nullable field", emptyList()) { it.withNullability(true) })


    /**
     * One class of a mutually recursive pair as the native describer gives it: the root class is the one definition,
     * and its partner is inline beneath it with a reference back to the root. Contracts rooted at either class
     * therefore unroll the cycle out of phase: where one has a reference, the other has a record.
     */
    private fun mutualRoot(rootId: DefinitionId, wrap: Wrap, partnerName: DataType = text): DataContract {
        val partner = record("other" to wrap.around(DataType.Reference(rootId)), "name" to partnerName)
        val root = record("other" to wrap.around(partner), "name" to text)
        return DataContract(root, definitions = mapOf(rootId to root))
    }


    /** The contract a Left nested in a Right gets from its enclosing Right's contract. */
    private fun leftInsideRight(wrap: Wrap): DataContract =
        wrap.segments.fold(mutualRoot(rightId, wrap).child(otherField)) { contract, segment ->
            contract.child(segment)
        }


    @Test
    fun mutualRecursionOutOfPhaseIsAcceptedThroughAListAMapAndANullableField() {
        for (wrap in wraps) {
            val nested = leftInsideRight(wrap)
            val own = mutualRoot(leftId, wrap)
            assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(nested, own), wrap.name)

            val required = DataContract(nested.structural.withNullability(false), definitions = nested.definitions)
            assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(own, required), wrap.name)
        }
    }


    @Test
    fun selfRecursionOutOfPhaseIsAccepted() {
        val chainId = DefinitionId("fixture.Chain")
        val unrolledId = DefinitionId("fixture.UnrolledChain")
        val chain = record("next" to DataType.Reference(chainId, nullable = true), "label" to text)
        val unrolled = record("next" to DataType.Reference(unrolledId, nullable = true), "label" to text)
        val expected = DataContract(DataType.Reference(chainId), definitions = mapOf(chainId to chain))
        val actual = DataContract(
            record("next" to unrolled.withNullability(true), "label" to text),
            definitions = mapOf(unrolledId to unrolled))

        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(expected, actual))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(actual, expected))
    }


    @Test
    fun anIncompatibleRecursivePairIsStillRejected() {
        for (wrap in wraps) {
            // The mismatch is one step around the cycle and after the recursive field, so it is found while the pair
            // that led to it is still assumed
            val incompatible = mutualRoot(leftId, wrap, partnerName = int32)
            assertRejectedAcceptance(DataTypeAlgebra.isAssignable(leftInsideRight(wrap), incompatible))
        }
    }


    @Test
    fun anAssumptionFromAFailedUnionBranchDoesNotDecideAnotherBranch() {
        val outerId = DefinitionId("fixture.Outer")
        val innerId = DefinitionId("fixture.Inner")
        // Expected and actual tables differ only in Outer.flag, so neither Outer nor Inner (whose member is an Outer)
        // is assignable
        fun definitions(flag: DataType) = mapOf(
            outerId to record("inner" to DataType.Reference(innerId), "flag" to flag),
            innerId to record("outer" to DataType.Reference(outerId, nullable = true)))
        val actual = DataContract(
            record("outer" to DataType.Reference(outerId), "inner" to DataType.Reference(innerId)),
            definitions = definitions(int32))

        // "both" accepts Inner while assuming Outer, then fails at Outer.flag; "inner" needs Inner alone
        val inner = DataContract(
            union(
                "both" to record("outer" to DataType.Reference(outerId), "inner" to DataType.Reference(innerId)),
                "inner" to record("inner" to DataType.Reference(innerId))),
            definitions = definitions(text))
        assertRejectedAcceptance(DataTypeAlgebra.isAssignable(inner, actual))

        // A pair that failed in one branch is not assumed in the next
        val retried = DataContract(
            union(
                "required" to record("outer" to DataType.Reference(outerId)),
                "nullable" to record("outer" to DataType.Reference(outerId, nullable = true))),
            definitions = definitions(text))
        assertRejectedAcceptance(DataTypeAlgebra.isAssignable(retried, actual))

        val compatible = DataContract(actual.structural, definitions = definitions(text))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(inner, compatible))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(retried, compatible))
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
