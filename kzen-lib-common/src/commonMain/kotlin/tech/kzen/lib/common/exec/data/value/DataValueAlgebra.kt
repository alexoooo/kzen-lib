package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.TypeAcceptance


object DataValueAlgebra {
    /**
     * Run-time assignability: [value] satisfies [expected] when its contract does, or when what it holds does.
     * A collection lifted without an expected contract types its elements by the ones it holds, so an empty one
     * has the element type `Dynamic?`, which no typed collection accepts, yet it holds no element to disagree with
     * any. So where the contracts disagree, a collection is compared through the contracts of the elements it
     * holds (none when it is empty), a record field by field, a null by the expected nullability; every other node
     * keeps its contract's verdict, as does each node's own nullability. Design-time assignability between
     * contracts stays [DataTypeAlgebra.isAssignable].
     *
     * A rejection is the contract comparison's. The walk visits only nodes whose contracts disagree.
     */
    fun isAssignable(expected: DataContract, value: DataValue): TypeAcceptance {
        val acceptance = DataTypeAlgebra.isAssignable(expected, value.contract)
        if (acceptance == TypeAcceptance.Accepted) {
            return acceptance
        }
        val holds = holds(expected.payload(), value.access, value.root, ::contractAccepts) &&
                metadataHolds(expected.metadata, value.metadata)
        return if (holds) TypeAcceptance.Accepted else acceptance
    }


    /**
     * [isAssignable]'s walk over the payload for a caller that compares payload types alone, where an opaque type
     * never accepts another: whether what [value] holds satisfies [expected] by that comparison.
     */
    internal fun holdsStructurally(expected: DataContract, value: DataValue): Boolean =
        holds(expected.payload(), value.access, value.root) { expectedContract, actualContract ->
            DataTypeAlgebra.isAssignable(expectedContract.structural, actualContract.structural) ==
                    TypeAcceptance.Accepted
        }


    /**
     * Explicit linear validation. Merely constructing or passing [DataValue] never calls this walk. Constraints
     * are enforced both as the value's contract declares them and as [expected] declares them.
     */
    fun validate(expected: DataContract, value: DataValue): List<DataProblem> {
        when (val acceptance = isAssignable(expected, value)) {
            TypeAcceptance.Accepted -> Unit
            is TypeAcceptance.Rejected -> return listOf(acceptance.problem)
        }

        val problems = mutableListOf<DataProblem>()
        val actual = value.payloadContract
        val declared = expected.payload().takeIf { it != actual }.constrainedOrNull()
        validateNode(value.access, value.root, actual, declared, emptyList(), required = true, problems)

        // Metadata is checked where the expectation declares it (assignability already required its presence)
        val expectedMetadata = expected.metadata
        val metadata = value.metadata
        if (expectedMetadata != null && metadata != null) {
            problems += validate(expectedMetadata.contract, metadata.value)
        }
        return problems
    }


    private fun contractAccepts(expected: DataContract, actual: DataContract): Boolean =
        DataTypeAlgebra.isAssignable(expected, actual) == TypeAcceptance.Accepted


    // References expand through each side's own definitions, as the contract comparison expands them
    private fun holds(
        expectedContract: DataContract,
        access: ValueAccess,
        node: DataNode,
        accepts: (DataContract, DataContract) -> Boolean
    ): Boolean {
        val actualContract = access.contract(node)
        if (accepts(expectedContract, actualContract)) {
            return true
        }
        val expected = expectedContract.expanded()
        val expectedType = expected.structural
        val actualType = actualContract.expanded().structural
        if (!expectedType.nullable && actualType.nullable) {
            return false
        }
        when (access.state(node)) {
            DataState.Present -> {}
            DataState.Null -> return expectedType.nullable
            DataState.Absent -> return false
        }
        return when {
            expectedType is DataType.Listing && actualType is DataType.Listing -> {
                val elementContract = expected.child(DataPathSegment.ListingElement)
                (0 until access.size(node)).all { index ->
                    holds(elementContract, access, access.element(node, index), accepts)
                }
            }

            expectedType is DataType.Mapping && actualType is DataType.Mapping -> {
                val size = access.size(node)
                val valueContract = expected.child(DataPathSegment.MappingValue)
                size == 0 || expectedType.key == actualType.key && (0 until size).all { index ->
                    holds(valueContract, access, access.entry(node, access.keyAt(node, index)), accepts)
                }
            }

            expectedType is DataType.Record && actualType is DataType.Record ->
                recordHolds(expected, expectedType, actualType, access, node, accepts)

            expectedType is DataType.Union && actualType !is DataType.Union ->
                expectedType.variants.any { variant ->
                    holds(expected.child(DataPathSegment.Variant(variant.id)), access, node, accepts)
                }

            else -> false
        }
    }


    // Fields match as DataTypeAlgebra matches them: in order, an optional actual only where the expected one is
    private fun recordHolds(
        expected: DataContract,
        expectedType: DataType.Record,
        actualType: DataType.Record,
        access: ValueAccess,
        node: DataNode,
        accepts: (DataContract, DataContract) -> Boolean
    ): Boolean {
        var actualIndex = 0
        for (expectedField in expectedType.fields) {
            while (actualIndex < actualType.fields.size && actualType.fields[actualIndex].id != expectedField.id) {
                actualIndex++
            }
            if (actualIndex == actualType.fields.size) {
                return false
            }
            if (!expectedField.optional && actualType.fields[actualIndex].optional) {
                return false
            }
            val field = access.field(node, expectedField.id)
            val absentOptional = expectedField.optional && access.state(field) == DataState.Absent
            if (!absentOptional &&
                    !holds(expected.child(DataPathSegment.Field(expectedField.id)), access, field, accepts)
            ) {
                return false
            }
            actualIndex++
        }
        return true
    }


    private fun metadataHolds(expected: MetadataContract?, metadata: ValueMetadata?): Boolean {
        expected ?: return true
        val actual = metadata?.contract ?: MetadataContract.empty
        return contractAccepts(expected.contract, actual.contract) ||
                metadata != null &&
                holds(expected.contract, metadata.value.access, metadata.value.root, ::contractAccepts)
    }


    // Only a contract that still constrains something beneath it is worth walking alongside the value
    private fun DataContract?.constrainedOrNull(): DataContract? =
        this?.takeIf { it.constraintsByPath.isNotEmpty() }


    private fun validateNode(
        access: ValueAccess,
        node: DataNode,
        expectedContract: DataContract,
        declared: DataContract?,
        path: List<DataPathSegment>,
        required: Boolean,
        problems: MutableList<DataProblem>
    ) {
        try {
            // The contract's own expansion: a reference at this position is its definition, one level
            val expected = expectedContract.expanded().structural
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

            val actual = access.contract(node)
            if (DataTypeAlgebra.isAssignable(expectedContract, actual) is TypeAcceptance.Rejected) {
                problems += problem(
                    DataProblem.incompatibleType,
                    "Node type ${actual.structural} is not assignable to $expected",
                    path)
                return
            }

            when (expected) {
                is DataType.Scalar -> {
                    val scalar = access.scalar(node)
                    validateScalar(scalar, expected.kind, path, problems)
                    validateConstraints(scalar, expectedContract, declared, path, problems)
                }
                is DataType.Record -> for (field in expected.fields) {
                    val segment = DataPathSegment.Field(field.id)
                    validateNode(
                        access,
                        access.field(node, field.id),
                        expectedContract.child(segment),
                        declared?.childOrNull(segment).constrainedOrNull(),
                        path + segment,
                        required = !field.optional,
                        problems)
                }
                is DataType.Listing -> {
                    val size = access.size(node)
                    val elementContract = expectedContract.child(DataPathSegment.ListingElement)
                    val elementDeclared = declared?.childOrNull(DataPathSegment.ListingElement).constrainedOrNull()
                    for (index in 0 until size) {
                        val segment = DataPathSegment.Element(index)
                        validateNode(
                            access, access.element(node, index), elementContract, elementDeclared,
                            path + segment, required = true, problems)
                    }
                }
                is DataType.Mapping -> {
                    val size = access.size(node)
                    val keyType = expected.key as? DataType.Scalar
                    val keyContract = expectedContract.child(DataPathSegment.MappingKey)
                    val keyDeclared = declared?.childOrNull(DataPathSegment.MappingKey).constrainedOrNull()
                    val valueContract = expectedContract.child(DataPathSegment.MappingValue)
                    val valueDeclared = declared?.childOrNull(DataPathSegment.MappingValue).constrainedOrNull()
                    for (index in 0 until size) {
                        val key = access.keyAt(node, index)
                        if (keyType != null) validateScalar(key, keyType.kind, path, problems)
                        val segment = keyType?.let { DataPathSegment.Entry(it.kind, key) }
                            ?: DataPathSegment.Element(index)
                        validateConstraints(key, keyContract, keyDeclared, path + segment, problems)
                        validateNode(
                            access, access.entry(node, key), valueContract, valueDeclared,
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
                            access, access.selected(node), expectedContract.child(segment),
                            declared?.childOrNull(segment).constrainedOrNull(),
                            path + segment, required = true, problems)
                    }
                }
                is DataType.Opaque -> access.native(node)
                is DataType.Dynamic -> {
                    // A value accepted by what it holds keeps the declared constraints on what it holds
                    val held = access.contract(node)
                    if (declared != null && held.structural !is DataType.Dynamic) {
                        validateNode(access, node, held, declared, path, required, problems)
                    }
                }
                is DataType.Reference -> problems += problem(
                    DataProblem.unresolvedReference,
                    "Type reference '${expected.id}' was not expanded",
                    path)
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
            // Canonical text, or a primitive long as native access yields one (the snapshot normalizes it to text)
            is ScalarKind.Integer,
            ScalarKind.Decimal -> value is TextExecutionValue || value is LongExecutionValue
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


    private fun validateConstraints(
        value: ScalarExecutionValue,
        own: DataContract,
        declared: DataContract?,
        path: List<DataPathSegment>,
        problems: MutableList<DataProblem>
    ) {
        val constraints = listOfNotNull(own, declared)
            .flatMap { it.constraintsByPath[DataTypePath.root].orEmpty() }
            .distinct()
        for (constraint in constraints) {
            constraint.violation(value)?.let { problems += problem(DataProblem.constraintViolation, it, path) }
        }
    }


    private fun problem(code: String, message: String, path: List<DataPathSegment>) =
        DataProblem(code, message, path)
}
