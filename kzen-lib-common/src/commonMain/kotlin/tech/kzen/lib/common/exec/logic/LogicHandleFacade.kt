package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


class LogicHandleFacade(
    private val logicRunExecutionId: LogicRunExecutionId,
    private val logicHandle: LogicHandle
) {
    fun start(
        originalObjectLocation: ObjectLocation,
        callerLocation: ObjectLocation?
    ): LogicExecutionFacade {
        return logicHandle.start(logicRunExecutionId, originalObjectLocation, callerLocation)
    }
}
