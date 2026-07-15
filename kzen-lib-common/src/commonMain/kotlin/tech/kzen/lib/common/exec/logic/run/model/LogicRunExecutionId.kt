package tech.kzen.lib.common.exec.logic.run.model


data class LogicRunExecutionId(
    val logicRunId: LogicRunId,
    val logicExecutionId: LogicExecutionId
) {
    companion object {
        /**
         * Fresh run identity. The execution-id half is a placeholder that reuses the run id's value:
         * actual executions are keyed by the engine's own per-invocation
         * [tech.kzen.lib.common.exec.engine.NodeId] ("n0", "n1", ...), so this minted execution id never
         * names a live execution — consumers of the composite (the Report run-dir stamp, OutputInfo)
         * only rely on the run-id half.
         */
        fun random(): LogicRunExecutionId {
            val executionId = LogicExecutionId.random()
            return LogicRunExecutionId(LogicRunId(executionId.value), executionId)
        }
    }
}
