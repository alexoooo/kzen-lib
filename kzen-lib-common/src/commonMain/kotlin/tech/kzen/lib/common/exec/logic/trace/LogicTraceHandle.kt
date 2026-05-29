package tech.kzen.lib.common.exec.logic.trace

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery


interface LogicTraceHandle {
    fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable


    fun set(
        logicTracePath: LogicTracePath,
        executionValue: ExecutionValue
    )


    fun clearAll(prefix: LogicTracePath)
}
