package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataProblem


object DataTypeAlgebra {
    fun isAssignable(expected: DataType, actual: DataType): TypeAcceptance =
        if (accepts(expected, actual)) {
            TypeAcceptance.Accepted
        }
        else {
            TypeAcceptance.Rejected(DataProblem(
                DataProblem.incompatibleType,
                "Actual type $actual is not assignable to expected type $expected"))
        }

    fun join(left: DataType, right: DataType): DataType {
        if (left == right) {
            return left
        }

        val nullable = left.nullable || right.nullable
        val leftRequired = left.withNullability(false)
        val rightRequired = right.withNullability(false)
        if (leftRequired == rightRequired) {
            return leftRequired.withNullability(nullable)
        }

        val joined = joinRequired(leftRequired, rightRequired)
        return joined.withNullability(nullable)
    }

    fun selectVariant(union: DataType.Union, actual: DataType): VariantSelection {
        if (actual is DataType.Dynamic) {
            return noMatch(union, actual)
        }

        val candidates = union.variants
            .filter { accepts(it.type, actual) }
            .map { it.id }

        return when (candidates.size) {
            0 -> noMatch(union, actual)
            1 -> VariantSelection.Selected(candidates.single())
            else -> VariantSelection.Ambiguous(candidates)
        }
    }

    fun validateVariant(
        union: DataType.Union,
        variant: VariantId,
        actual: DataType
    ): TypeAcceptance {
        val selected = union.variants.firstOrNull { it.id == variant }
            ?: return TypeAcceptance.Rejected(DataProblem(
                DataProblem.unionVariantUnknown,
                "Union has no variant '$variant'"))

        return isAssignable(selected.type, actual)
    }

    private fun accepts(expected: DataType, actual: DataType): Boolean {
        if (!expected.nullable && actual.nullable) {
            return false
        }
        if (expected.nullable || actual.nullable) {
            return accepts(expected.withNullability(false), actual.withNullability(false))
        }
        if (expected is DataType.Dynamic) {
            return true
        }
        if (actual is DataType.Dynamic) {
            return false
        }
        if (expected is DataType.Opaque) {
            return false
        }

        if (expected is DataType.Union) {
            return if (actual is DataType.Union) {
                actual.variants.all { actualVariant ->
                    expected.variants.any { expectedVariant ->
                        accepts(expectedVariant.type, actualVariant.type)
                    }
                }
            }
            else {
                expected.variants.any { accepts(it.type, actual) }
            }
        }
        if (actual is DataType.Union) {
            return actual.variants.all { accepts(expected, it.type) }
        }

        return when {
            expected is DataType.Scalar && actual is DataType.Scalar ->
                acceptsScalar(expected.kind, actual.kind)

            expected is DataType.Record && actual is DataType.Record ->
                acceptsRecord(expected, actual)

            expected is DataType.Listing && actual is DataType.Listing ->
                accepts(expected.element, actual.element)

            expected is DataType.Mapping && actual is DataType.Mapping ->
                expected.key == actual.key && accepts(expected.value, actual.value)

            else -> false
        }
    }

    private fun acceptsRecord(expected: DataType.Record, actual: DataType.Record): Boolean {
        var actualIndex = 0
        for (expectedField in expected.fields) {
            while (actualIndex < actual.fields.size && actual.fields[actualIndex].id != expectedField.id) {
                actualIndex++
            }
            if (actualIndex == actual.fields.size) {
                return false
            }

            val actualField = actual.fields[actualIndex]
            if (!expectedField.optional && actualField.optional) {
                return false
            }
            if (!accepts(expectedField.type, actualField.type)) {
                return false
            }
            actualIndex++
        }
        return true
    }

    private fun acceptsScalar(expected: ScalarKind, actual: ScalarKind): Boolean =
        when {
            expected == actual -> true
            expected == ScalarKind.Decimal && actual is ScalarKind.Integer -> true
            expected is ScalarKind.Floating && actual is ScalarKind.Floating ->
                expected.bits >= actual.bits
            expected is ScalarKind.Floating && actual is ScalarKind.Integer ->
                integerPrecision(actual) <= if (expected.bits == 32) 24 else 53
            expected is ScalarKind.Integer && actual is ScalarKind.Integer ->
                integerAccepts(expected, actual)
            else -> false
        }

    private fun integerAccepts(
        expected: ScalarKind.Integer,
        actual: ScalarKind.Integer
    ): Boolean {
        if (!expected.signed && actual.signed) {
            return false
        }
        if (expected.bits == null) {
            return expected.signed || !actual.signed
        }
        if (actual.bits == null) {
            return false
        }
        val requiredBits = if (expected.signed && !actual.signed) actual.bits + 1 else actual.bits
        return expected.bits >= requiredBits
    }

    private fun integerPrecision(kind: ScalarKind.Integer): Int =
        kind.bits?.let { if (kind.signed) it - 1 else it } ?: Int.MAX_VALUE

    private fun joinRequired(left: DataType, right: DataType): DataType =
        when {
            left is DataType.Scalar && right is DataType.Scalar ->
                joinScalar(left.kind, right.kind)?.let { DataType.Scalar(it) } ?: DataType.Dynamic(false)

            left is DataType.Record && right is DataType.Record &&
                    left.fields.map { it.id } == right.fields.map { it.id } ->
                DataType.Record(left.fields.zip(right.fields).map { (leftField, rightField) ->
                    DataField(
                        leftField.id,
                        join(leftField.type, rightField.type),
                        leftField.optional || rightField.optional)
                })

            left is DataType.Listing && right is DataType.Listing ->
                DataType.Listing(join(left.element, right.element))

            left is DataType.Mapping && right is DataType.Mapping && left.key == right.key ->
                DataType.Mapping(left.key, join(left.value, right.value))

            else -> DataType.Dynamic(false)
        }

    private fun joinScalar(left: ScalarKind, right: ScalarKind): ScalarKind? {
        if (left == right) {
            return left
        }
        if (left == ScalarKind.Decimal && right is ScalarKind.Integer ||
            right == ScalarKind.Decimal && left is ScalarKind.Integer
        ) {
            return ScalarKind.Decimal
        }
        if (left is ScalarKind.Floating && right is ScalarKind.Floating) {
            return ScalarKind.Floating(maxOf(left.bits, right.bits))
        }
        if (left is ScalarKind.Integer && right is ScalarKind.Integer) {
            return joinInteger(left, right)
        }
        return null
    }

    private fun joinInteger(
        left: ScalarKind.Integer,
        right: ScalarKind.Integer
    ): ScalarKind.Integer {
        if (left.bits == null || right.bits == null) {
            return ScalarKind.Integer(null, left.signed || right.signed)
        }
        val signed = left.signed || right.signed
        val leftRequired = left.bits + if (signed && !left.signed) 1 else 0
        val rightRequired = right.bits + if (signed && !right.signed) 1 else 0
        val required = maxOf(leftRequired, rightRequired)
        val width = listOf(8, 16, 32, 64).firstOrNull { it >= required }
        return ScalarKind.Integer(width, signed)
    }

    private fun noMatch(union: DataType.Union, actual: DataType): VariantSelection.NoMatch =
        VariantSelection.NoMatch(DataProblem(
            DataProblem.unionVariantNoMatch,
            "No variant in $union accepts actual type $actual"))
}
