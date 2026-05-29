package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.model.LogicDefinition
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


interface Logic {
    fun define(): LogicDefinition

    fun execute(
        logicHandle: LogicHandle,
        logicTraceHandle: LogicTraceHandle,
        logicRunExecutionId: LogicRunExecutionId,
        logicControl: LogicControl,
        objectStableMapper: ObjectStableMapper
    ): LogicExecution
}
