package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.TypeAcceptance


object DataValueAlgebra {
    /** Explicit linear validation. Merely constructing or passing [DataValue] never calls this walk. */
    fun validate(expected: DataContract, value: DataValue): List<DataProblem> {
        when (val acceptance = DataTypeAlgebra.isAssignable(expected.structural, value.type)) {
            TypeAcceptance.Accepted -> Unit
            is TypeAcceptance.Rejected -> return listOf(acceptance.problem)
        }

        val problems = mutableListOf<DataProblem>()
        validateNode(value.access, value.root, value.type, emptyList(), required = true, problems)
        return problems
    }


    private fun validateNode(
        access: ValueAccess,
        node: DataNode,
        expected: DataType,
        path: List<DataPathSegment>,
        required: Boolean,
        problems: MutableList<DataProblem>
    ) {
        try {
            val state = access.state(node)
            if (state == DataState.Absent) {
                if (required) problems += problem(
                    DataProblem.missingValue, "Required value is absent", path)
                return
            }
            if (state == DataState.Null) {
                if (!expected.nullable) problems += problem(
                    DataProblem.invalidState, "Null is not allowed by $expected", path)
                return
            }

            val actual = access.contract(node).structural
            if (DataTypeAlgebra.isAssignable(expected, actual) is TypeAcceptance.Rejected) {
                problems += problem(
                    DataProblem.incompatibleType,
                    "Node type $actual is not assignable to $expected",
                    path)
                return
            }

            when (expected) {
                is DataType.Scalar -> validateScalar(access.scalar(node), expected.kind, path, problems)
                is DataType.Record -> for (field in expected.fields) {
                    val segment = DataPathSegment.Field(field.id)
                    validateNode(
                        access,
                        access.field(node, field.id),
                        field.type,
                        path + segment,
                        required = !field.optional,
                        problems)
                }
                is DataType.Listing -> {
                    val size = access.size(node)
                    for (index in 0 until size) {
                        val segment = DataPathSegment.Element(index)
                        validateNode(
                            access, access.element(node, index), expected.element,
                            path + segment, required = true, problems)
                    }
                }
                is DataType.Mapping -> {
                    val size = access.size(node)
                    val keyType = expected.key as? DataType.Scalar
                    for (index in 0 until size) {
                        val key = access.keyAt(node, index)
                        if (keyType != null) validateScalar(key, keyType.kind, path, problems)
                        val segment = keyType?.let { DataPathSegment.Entry(it.kind, key) }
                            ?: DataPathSegment.Element(index)
                        validateNode(
                            access, access.entry(node, key), expected.value,
                            path + segment, required = true, problems)
                    }
                }
                is DataType.Union -> {
                    val active = access.activeVariant(node)
                    val variant = expected.variants.firstOrNull { it.id == active }
                    if (variant == null) {
                        problems += problem(
                            DataProblem.unionVariantUnknown,
                            "Union has no active variant '$active'",
                            path)
                    }
                    else {
                        val segment = DataPathSegment.Variant(active)
                        validateNode(
                            access, access.selected(node), variant.type,
                            path + segment, required = true, problems)
                    }
                }
                is DataType.Opaque -> access.native(node)
                is DataType.Dynamic -> Unit
            }
        }
        catch (e: DataAccessException) {
            problems += e.problem
        }
        catch (e: RuntimeException) {
            problems += problem(
                DataProblem.invalidValue,
                e.message ?: "Value access failed",
                path)
        }
    }


    private fun validateScalar(
        value: ScalarExecutionValue,
        kind: ScalarKind,
        path: List<DataPathSegment>,
        problems: MutableList<DataProblem>
    ) {
        val valid = when (kind) {
            ScalarKind.Boolean -> value is BooleanExecutionValue
            is ScalarKind.Integer,
            ScalarKind.Decimal,
            ScalarKind.Text,
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid -> value is TextExecutionValue
            is ScalarKind.Floating -> value is NumberExecutionValue
            ScalarKind.Binary -> value is BinaryExecutionValue
        }
        if (!valid) {
            problems += problem(
                DataProblem.invalidValue,
                "Scalar $value does not conform to $kind",
                path)
        }
    }


    private fun problem(code: String, message: String, path: List<DataPathSegment>) =
        DataProblem(code, message, path)
}
