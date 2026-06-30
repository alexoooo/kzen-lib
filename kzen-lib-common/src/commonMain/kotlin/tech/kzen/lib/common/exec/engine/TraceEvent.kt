package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One entry in the run's append-only history timeline (logic-spec §7). The history is the raw event log;
 * the live latest-value view ([Node.live]) is just a projection of it (last write per [address]). Both
 * write modes therefore come from a single stream — there is no second parallel structure.
 *
 * [sequence] is the fold index assigned by the single writer (deterministic total order across all parallel
 * spines, with no atomic counter). [address] is null for [Execution.log]-style timeline events that aren't
 * tied to a live address.
 */
data class TraceEvent(
    val sequence: Long,
    val nodeId: NodeId,
    val stableId: ObjectStableId,
    val address: Address?,
    val value: ExecutionValue
)
