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
 * [MetadataContract], built once here however many values share it — or once per
 * [tech.kzen.lib.common.exec.data.value.overlay.MetadataShape] however many metadata records it makes. It is
 * typically detached ([LiteralDataValues], [DataSnapshot]) or composed from detached parts ([DataOverlay]), so it
 * stays valid after the payload is released and never keeps the payload's native resources alive. Values derived
 * from one input share one instance.
 */
class ValueMetadata private constructor(
    val access: ValueAccess,
    val root: DataNode,
    private val contracts: Contracts
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


        /**
         * Metadata whose contract is already known: [contracts] must describe [access] at [root], unchecked. Only a
         * `MetadataShape` makes it, having checked every part's contract against the one it composed.
         */
        internal fun shaped(access: ValueAccess, root: DataNode, contracts: Contracts): ValueMetadata =
            ValueMetadata(access, root, contracts)
    }


    /** Fails by name unless the record at [root] is plain data (see [MetadataContract]). */
    constructor(access: ValueAccess, root: DataNode):
        this(access, root, Contracts(MetadataContract.of(access.contract(root))))


    /** The metadata record as a plain value (without metadata), to read its fields. */
    val value: DataValue = DataValue(access, root)

    val contract: MetadataContract
        get() = contracts.metadata

    val type: DataType.Record
        get() = contract.structural


    /** [payload] with this metadata's contract as its [DataContract.metadata]. */
    internal fun combine(payload: DataContract): DataContract =
        contracts.combine(payload)


    /**
     * A metadata contract with the last payload contract it was combined with and the result: metadata records
     * sharing it (one instance's values, or a shape's records) share the combined contract while the payload's is
     * the same object. Unsynchronized, the entry is an immutable pair: a race only recomputes it.
     */
    internal class Contracts(
        val metadata: MetadataContract
    ) {
        private var combined: CombinedContract? = null

        fun combine(payload: DataContract): DataContract {
            val cached = combined
            if (cached != null && cached.payload === payload) {
                return cached.contract
            }
            val contract = payload.withMetadata(metadata)
            combined = CombinedContract(payload, contract)
            return contract
        }
    }


    private class CombinedContract(
        val payload: DataContract,
        val contract: DataContract)
}
