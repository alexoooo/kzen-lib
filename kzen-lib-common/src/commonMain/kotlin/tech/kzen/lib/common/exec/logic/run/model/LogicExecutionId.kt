package tech.kzen.lib.common.exec.logic.run.model


/**
 * Same logic can be executed multiple times within each run
 */
data class LogicExecutionId(
    val value: String
) {
    override fun toString(): String {
        return value
    }
}
