package tech.kzen.lib.common.exec.logic.run.model


/**
 * There's at most one top-level run active at any given time.
 */
data class LogicRunId(
    val value: String
) {
    override fun toString(): String {
        return value
    }
}
