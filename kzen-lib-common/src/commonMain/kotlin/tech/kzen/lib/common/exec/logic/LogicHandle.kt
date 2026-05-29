package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicHandle {
    fun start(
        logicRunExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation
    ): LogicExecutionFacade
}
