package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType


/**
 * A live, read-only root view. Content equality is deliberately not defined on live values.
 *
 * [access] and [root] are the payload. [metadata] describes the whole value without being part of it (see
 * [ValueMetadata] and [DataContract.metadata]).
 */
class DataValue(
    val access: ValueAccess,
    val root: DataNode,
    val metadata: ValueMetadata? = null
) {
    init {
        if (access.state(root) == DataState.Absent) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidState,
                "A DataValue root must not be absent"))
        }
    }


    /** The payload's contract with the [metadata]'s contract as its [DataContract.metadata]. */
    val contract: DataContract
        get() {
            val payload = access.contract(root)
            return metadata?.combine(payload) ?: payload
        }

    /** The payload's contract alone. */
    val payloadContract: DataContract
        get() = access.contract(root)

    val type: DataType
        get() = access.contract(root).structural


    /** This payload with [metadata] as its metadata, replacing any it had. */
    fun withMetadata(metadata: ValueMetadata?): DataValue =
        if (metadata === this.metadata) this
        else DataValue(access, root, metadata)


    /** This payload without metadata. */
    fun payload(): DataValue =
        withMetadata(null)
}
