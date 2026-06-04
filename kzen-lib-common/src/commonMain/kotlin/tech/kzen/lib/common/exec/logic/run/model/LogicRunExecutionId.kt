package tech.kzen.lib.common.exec.logic.run.model


data class LogicRunExecutionId(
    val logicRunId: LogicRunId,
    val logicExecutionId: LogicExecutionId
) {
    companion object {
        /**
         * Fresh run with its initial execution: by convention the initial execution ID
         * shares the run ID's value.
         */
        fun random(): LogicRunExecutionId {
            val executionId = LogicExecutionId.random()
            return LogicRunExecutionId(LogicRunId(executionId.value), executionId)
        }
    }
}
