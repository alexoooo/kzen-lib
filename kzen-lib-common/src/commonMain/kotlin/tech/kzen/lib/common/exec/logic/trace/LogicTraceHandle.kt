package tech.kzen.lib.common.exec.logic.trace

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.service.store.normal.ObjectStableId


interface LogicTraceHandle {
    fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable


    fun set(
        logicTracePath: LogicTracePath,
        executionValue: ExecutionValue
    )


    // Append an immutable event to the run's history timeline. Unlike set (latest-per-path, wiped by
    // clearAll), appended events are retained — e.g. across loop iterations that clear the live paths.
    // Value-agnostic: any Logic can record any value (a screenshot is just a BinaryExecutionValue).
    fun append(
        objectStableId: ObjectStableId,
        value: ExecutionValue
    )


    fun clearAll(prefix: LogicTracePath)
}
