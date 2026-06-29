package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicHandle {
    // callerLocation is the call-site that launched this execution (the Run step / Run worker /
    // Run-logic vertex), recorded on the execution so its trace events can be attributed to the
    // specific invocation rather than only to the document that ran. Null for a run's root execution.
    fun start(
        logicRunExecutionId: LogicRunExecutionId,
        originalObjectLocation: ObjectLocation,
        callerLocation: ObjectLocation?
    ): LogicExecutionFacade
}
