package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.MetadataContract


/**
 * A value's metadata ([DataValue.metadata]): a record of plain data that describes the whole value without being
 * part of its payload, like a message's headers. Where the value came from, values derived from its name or
 * container, and a `parent` field holding the metadata of the value it was derived from.
 *
 * It has no metadata of its own: there is no slot for it, so value metadata never nests. Its [contract] is a
 * [MetadataContract], built once here however many values share it. It is typically detached
 * ([LiteralDataValues], [DataSnapshot]) or composed from detached parts ([DataOverlay]), so it stays valid after the
 * payload is released and never keeps the payload's native resources alive. Values derived from one input share
 * one instance.
 */
class ValueMetadata(
    val access: ValueAccess,
    val root: DataNode
) {
    companion object {
        /** [record] as metadata; fails by name when [record] has metadata of its own. */
        fun of(record: DataValue): ValueMetadata {
            if (record.metadata != null) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidContract,
                    "Value metadata must not have metadata of its own"))
            }
            return ValueMetadata(record.access, record.root)
        }
    }


    /** The metadata record as a plain value (without metadata), to read its fields. */
    val value: DataValue = DataValue(access, root)

    /** Fails by name unless the record is plain data (see [MetadataContract]). */
    val contract: MetadataContract = MetadataContract.of(access.contract(root))

    val type: DataType.Record
        get() = contract.structural


    // The last payload contract this metadata was combined with and the result; values sharing it hit it
    private var combined: CombinedContract? = null


    /** [payload] with this metadata's contract as its [DataContract.metadata]. */
    internal fun combine(payload: DataContract): DataContract {
        val cached = combined
        if (cached != null && cached.payload === payload) {
            return cached.contract
        }
        val contract = payload.withMetadata(contract)
        combined = CombinedContract(payload, contract)
        return contract
    }


    private class CombinedContract(
        val payload: DataContract,
        val contract: DataContract)
}
