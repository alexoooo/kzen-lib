package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The terminal result of an execution (logic-spec §4 outcome taxonomy). Pause is deliberately NOT here —
 * a paused computation is non-terminal and modelled as a suspended checkpoint (see [NodeStatus.Suspended]),
 * not a returned outcome.
 */
sealed interface Outcome {
    data class Success(val value: TupleValue): Outcome

    /**
     * A failed execution. [at] is the stable id of the element the failure ORIGINATED at — the engine stamps
     * the failing node's own id when a throwable first escapes it, and a child failure re-thrown up through
     * [Execution.host] carries the child's [at] UNCHANGED (mirroring the spec §4 pause-reason "propagates
     * upward unchanged" principle), so a whole-run failure still names where it really came from. Null when
     * the origin is unknown.
     */
    data class Failed(val message: String, val at: ObjectStableId? = null): Outcome

    data object Cancelled: Outcome
}
