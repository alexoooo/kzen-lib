package tech.kzen.lib.common.exec.data.value.overlay

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata


/**
 * Metadata composed as `ValueMetadata.of(DataOverlay.record(base, fields))` composes it, prepared once for the
 * [base] metadata's contract and the set [fields]' contracts: the composed record's contract and [contract] are
 * built and checked here, so each [metadata] record costs its composition alone. Records of one shape also share
 * their combination with a payload contract ([DataValue.contract]).
 *
 * Reads exactly as the overlay it stands for: the same contract and field order (a replaced field keeps its
 * position, appended fields follow), fields, snapshot and native source. The contract failures of
 * [DataOverlay.contract] and [MetadataContract.of] are raised here, at construction.
 *
 * A record is made only from parts whose contracts are this shape's — the same object, or an equal one — and fails
 * by name otherwise, never falling back to composing a contract of its own: a caller whose input contract can
 * change keeps a shape per contract, checking [fits] before reusing one.
 */
class MetadataShape(
    val base: MetadataContract?,
    fields: List<Pair<FieldId, DataContract>>
) {
    /** The set fields' ids, in the order [metadata] takes their values. */
    val fields: List<FieldId> = fields.map { it.first }

    private val fieldContracts: List<DataContract> = fields.map { it.second }

    private val layout = OverlayLayout(base?.contract, fields)

    private val contracts = ValueMetadata.Contracts(MetadataContract.of(layout.contract))


    /** The contract of every record this shape makes. */
    val contract: MetadataContract
        get() = contracts.metadata


    /** Whether [metadata] accepts [base] and [values]: their contracts are this shape's. */
    fun fits(base: ValueMetadata?, values: List<DataValue>): Boolean =
        misfit(base, values) == null


    /**
     * The metadata [base] (none when null) with [values] set as [fields], in that order; [base] and each value
     * must have this shape's contracts ([fits]).
     */
    fun metadata(base: ValueMetadata?, values: List<DataValue>): ValueMetadata {
        val problem = misfit(base, values)
        if (problem != null) {
            throw DataException(DataProblem(DataProblem.invalidContract, problem))
        }
        return ValueMetadata.shaped(
            OverlayAccess(layout, base?.value, values), OverlayAccess.root, contracts)
    }


    // Identity first: parts made by one shape (or one literal contract) share contract objects
    private fun misfit(base: ValueMetadata?, values: List<DataValue>): String? {
        val baseContract = base?.contract
        if (baseContract !== this.base && baseContract != this.base) {
            return "Base metadata ${baseContract?.structural} does not fit the shape's ${this.base?.structural}"
        }
        if (values.size != fieldContracts.size) {
            return "Expected ${fieldContracts.size} metadata field values ($fields), not ${values.size}"
        }
        for (index in fieldContracts.indices) {
            val expected = fieldContracts[index]
            val actual = values[index].payloadContract
            if (actual !== expected && actual != expected) {
                return "Metadata field '${fields[index]}' value ${actual.structural} " +
                        "does not fit the shape's ${expected.structural}"
            }
        }
        return null
    }
}
