package tech.kzen.lib.common.exec.engine


/**
 * The lifecycle state of one node in the execution tree (logic-spec §4). [Suspended] is the non-terminal
 * paused state, carrying its [PauseReason]; [Terminal] wraps the settled [Outcome].
 */
sealed interface NodeStatus {
    data object Running: NodeStatus

    data class Suspended(val reason: PauseReason): NodeStatus

    data class Terminal(val outcome: Outcome): NodeStatus
}
