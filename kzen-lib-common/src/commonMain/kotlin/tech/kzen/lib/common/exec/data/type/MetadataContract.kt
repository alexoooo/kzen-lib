package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


/**
 * The contract of a value's metadata ([DataContract.metadata]), the type side of
 * [tech.kzen.lib.common.exec.data.value.ValueMetadata]: a non-nullable record of plain data, with the named
 * [definitions] and [constraintsByPath] a record may carry.
 *
 * It has no native metadata and no metadata of its own: there is no slot for either, so a metadata contract never
 * nests and never describes a native resource. Opaque members, which would need native metadata, fail by name.
 * [contract] reads it as a plain payload contract, to navigate its fields or compare it.
 */
class MetadataContract(
    val structural: DataType.Record,
    definitions: Map<DefinitionId, DataType> = emptyMap(),
    constraintsByPath: Map<DataTypePath, List<DataConstraint>> = emptyMap()
) {
    companion object {
        /** The metadata contract with no fields, which composed metadata starts from. */
        val empty = MetadataContract(DataType.Record(emptyList()))


        /** [contract] as a metadata contract; fails by name unless it is a record of plain data without metadata. */
        fun of(contract: DataContract): MetadataContract {
            val problem = when {
                contract.structural !is DataType.Record -> "must be a record, not ${contract.structural}"
                contract.metadata != null -> "must not have metadata of its own"
                contract.nativeByPath.isNotEmpty() || contract.definitionNatives.isNotEmpty() ->
                    "must be plain data, without native metadata"
                else -> null
            }
            if (problem != null) {
                throw invalid(problem)
            }
            return MetadataContract(
                contract.structural as DataType.Record, contract.definitions, contract.constraintsByPath)
        }


        fun ofExecutionValue(executionValue: ExecutionValue): MetadataContract =
            of(DataContract.ofExecutionValue(executionValue))


        private fun invalid(problem: String): DataException =
            DataException(DataProblem(DataProblem.invalidContract, "Value metadata $problem"))
    }


    init {
        if (structural.nullable) {
            throw invalid("must not be nullable")
        }
        if ((listOf(structural) + definitions.values).any { type -> type.walk().any { it.second is DataType.Opaque } }) {
            throw invalid("must be plain data, without opaque members")
        }
    }


    /** The metadata record's contract as a plain payload contract (no native metadata, no metadata). */
    val contract: DataContract = DataContract(
        structural, definitions = definitions, constraintsByPath = constraintsByPath)

    val definitions: Map<DefinitionId, DataType>
        get() = contract.definitions

    val constraintsByPath: Map<DataTypePath, List<DataConstraint>>
        get() = contract.constraintsByPath


    fun asExecutionValue(): MapExecutionValue =
        contract.asExecutionValue()

    override fun equals(other: Any?): Boolean =
        this === other || other is MetadataContract && contract == other.contract

    override fun hashCode(): Int =
        contract.hashCode()

    override fun toString(): String =
        "MetadataContract($structural)"
}
