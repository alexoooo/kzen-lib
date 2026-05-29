package tech.kzen.lib.server.exec.logic.trace

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.LogicTrace
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


class LogicTraceStore(
    private val objectStableMapper: ObjectStableMapper
):
    LogicTrace
{
    //-----------------------------------------------------------------------------------------------------------------
    private data class RunExecution(
        val runExecutionId: LogicRunExecutionId
    )


    private class TraceBuffer {
        val values = ConcurrentHashMap<LogicTracePath, ExecutionValue>()
        val callbacks = CopyOnWriteArrayList<(LogicTraceQuery) -> Unit>()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val history = ConcurrentHashMap<RunExecution, TraceBuffer>()
    private val objectLocationHistory = ConcurrentHashMap<ObjectLocation, LogicRunExecutionId>()


    //-----------------------------------------------------------------------------------------------------------------
    fun handle(
        runExecutionId: LogicRunExecutionId,
        objectLocation: ObjectLocation
    ): LogicTraceHandle {
        val buffer = getOrCreateBuffer(runExecutionId, objectLocation)

        return object: LogicTraceHandle {
            override fun register(callback: (LogicTraceQuery) -> Unit): AutoCloseable {
                buffer.callbacks.add(callback)
                return AutoCloseable {
                    buffer.callbacks.remove(callback)
                }
            }

            override fun set(logicTracePath: LogicTracePath, executionValue: ExecutionValue) {
                buffer.values[logicTracePath] = executionValue
            }

            override fun clearAll(prefix: LogicTracePath) {
                val pathsToClear = buffer.values.keys.filter { storedPath ->
                    matchesPrefix(storedPath, prefix)
                }
                for (pathToClear in pathsToClear) {
                    buffer.values.remove(pathToClear)
                }
            }
        }
    }


    private fun getOrCreateBuffer(
        runExecutionId: LogicRunExecutionId,
        objectLocation: ObjectLocation
    ): TraceBuffer {
        val previous = objectLocationHistory[objectLocation]
        if (previous != null && previous.logicRunId != runExecutionId.logicRunId) {
            evict(previous.logicRunId)
        }
        objectLocationHistory[objectLocation] = runExecutionId

        val runExecution = RunExecution(runExecutionId)
        return history.getOrPut(runExecution) { TraceBuffer() }
    }


    private fun matchesPrefix(
        storedPath: LogicTracePath,
        prefix: LogicTracePath
    ): Boolean {
        return resolveStoredPath(storedPath)
            ?.startsWith(prefix)
            ?: false
    }


    private fun resolveStoredPath(
        storedPath: LogicTracePath
    ): LogicTracePath? {
        val stableId = storedPath.objectStableId()
            ?: return storedPath
        val currentLocation =
            try {
                objectStableMapper.objectLocation(stableId)
            }
            catch (_: IllegalArgumentException) {
                return null
            }
        return LogicTracePath.ofObjectLocation(currentLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun mostRecent(objectLocation: ObjectLocation): LogicRunExecutionId? {
        return objectLocationHistory[objectLocation]
    }


    override fun clear(objectLocation: ObjectLocation): Boolean {
        return objectLocationHistory.remove(objectLocation) != null
    }


    override fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ):
        LogicTraceSnapshot?
    {
        val runExecution = RunExecution(logicRunExecutionId)

        val buffer = history[runExecution]
            ?: return null

        buffer.callbacks.forEach { it(logicTraceQuery) }

        val resolvedValues = mutableMapOf<LogicTracePath, ExecutionValue>()
        for ((storedPath, value) in buffer.values) {
            val resolvedPath = resolveStoredPath(storedPath)
                ?: continue
            if (logicTraceQuery.match(resolvedPath)) {
                resolvedValues[resolvedPath] = value
            }
        }

        return LogicTraceSnapshot(resolvedValues)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun evict(logicRunId: LogicRunId) {
        history.keys.removeAll { it.runExecutionId.logicRunId == logicRunId }
        objectLocationHistory.entries.removeAll { it.value.logicRunId == logicRunId }
    }
}
