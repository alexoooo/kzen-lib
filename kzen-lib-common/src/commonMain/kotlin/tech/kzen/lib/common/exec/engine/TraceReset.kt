package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One [Execution.resetEmitted] signal: the resetting node, its stable id (for address→path routing,
 * mirroring [TraceEvent.stableId]), the live addresses it cleared on itself, and the call-sites whose
 * hosted invocations (transitively including their hosted descendants) are superseded. Delivered
 * synchronously BEFORE [Execution.resetEmitted] returns, so a consumer that drains pending history and
 * then clears is ordered correctly against the same spine's subsequent emits.
 */
class TraceReset(
    val nodeId: NodeId,
    val stableId: ObjectStableId,
    val addresses: List<Address>,
    val callSites: List<ObjectStableId>
)
