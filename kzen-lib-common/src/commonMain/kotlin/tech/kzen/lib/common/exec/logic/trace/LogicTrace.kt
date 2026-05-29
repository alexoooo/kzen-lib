package tech.kzen.lib.common.exec.logic.trace

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicTrace {
    fun mostRecent(
        objectLocation: ObjectLocation
    ): LogicRunExecutionId?


    fun clear(
        objectLocation: ObjectLocation
    ): Boolean


    fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?
}
