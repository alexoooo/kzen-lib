package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.engine.resource.ResourceOwners


/**
 * A live, read-only root view. Content equality is deliberately not defined on live values.
 *
 * [access] and [root] are the payload. [metadata] describes the whole value without being part of it (see
 * [ValueMetadata] and [DataContract.metadata]). [owners] are the resources the value's lifetime depends on —
 * null for the common unowned value, so reading it costs a field read and nothing else; a view navigated or
 * derived from an owned value carries the same owners by construction ([child], [withMetadata]).
 */
class DataValue(
    val access: ValueAccess,
    val root: DataNode,
    val metadata: ValueMetadata? = null,
    val owners: ResourceOwners? = null
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
        else DataValue(access, root, metadata, owners)


    /** This payload without metadata. */
    fun payload(): DataValue =
        withMetadata(null)


    /** This payload depending on [owners] (replacing what it depended on); null makes it unowned. */
    fun withOwners(owners: ResourceOwners?): DataValue =
        if (owners === this.owners) this
        else DataValue(access, root, metadata, owners?.takeUnless { it.isEmpty })


    /** The view rooted at [node] of the same backing: no metadata of its own, the same owners. */
    fun child(node: DataNode): DataValue =
        DataValue(access, node, null, owners)


    /**
     * Fails by name when a resource this value depends on has been closed: a read through a closed owner is a
     * use-after-close, caught here on the next read rather than read silently. Free for an unowned value.
     */
    fun requireOpen() {
        val closed = owners?.closed() ?: return
        throw DataAccessException(DataProblem(
            DataProblem.invalidState,
            "$closed was closed and can no longer be read"))
    }


    /** The root native, after [requireOpen]. */
    fun native(): Any {
        requireOpen()
        return access.native(root)
    }
}
