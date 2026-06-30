package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * The terminal result of an execution (logic-spec §4 outcome taxonomy). Pause is deliberately NOT here —
 * a paused computation is non-terminal and modelled as a suspended checkpoint (see [NodeStatus.Suspended]),
 * not a returned outcome.
 */
sealed interface Outcome {
    data class Success(val value: TupleValue): Outcome

    data class Failed(val message: String): Outcome

    data object Cancelled: Outcome
}
