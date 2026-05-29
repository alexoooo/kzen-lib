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
import tech.kzen.lib.common.service.store.normal.ObjectStableId
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

    // The run-scope entry point is keyed by its stable id (not its current ObjectLocation) so the
    // "most recent run" / "Reset" association survives a rename of the script's root document.
    private val stableIdHistory = ConcurrentHashMap<ObjectStableId, LogicRunExecutionId>()


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
        val stableId = objectStableMapper.objectStableId(objectLocation)
        val previous = stableIdHistory[stableId]
        if (previous != null && previous.logicRunId != runExecutionId.logicRunId) {
            evict(previous.logicRunId)
        }
        stableIdHistory[stableId] = runExecutionId

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


    // Trace values cross the wire keyed by ObjectStableId; the client translates each path to the
    // current ObjectLocation via its own mapper. Keep the stable key here, but drop entries whose
    // object no longer exists so deleted steps don't linger in the snapshot.
    private fun retainStoredPath(
        storedPath: LogicTracePath
    ): LogicTracePath? {
        val stableId = storedPath.objectStableId()
            ?: return storedPath
        return try {
            objectStableMapper.objectLocation(stableId)
            storedPath
        }
        catch (_: IllegalArgumentException) {
            null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun mostRecent(objectLocation: ObjectLocation): LogicRunExecutionId? {
        val stableId = objectStableMapper.objectStableId(objectLocation)
        return stableIdHistory[stableId]
    }


    override fun clear(objectLocation: ObjectLocation): Boolean {
        val stableId = objectStableMapper.objectStableId(objectLocation)
        return stableIdHistory.remove(stableId) != null
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

        val retainedValues = mutableMapOf<LogicTracePath, ExecutionValue>()
        for ((storedPath, value) in buffer.values) {
            val retainedPath = retainStoredPath(storedPath)
                ?: continue
            if (logicTraceQuery.match(retainedPath)) {
                retainedValues[retainedPath] = value
            }
        }

        return LogicTraceSnapshot(retainedValues)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun evict(logicRunId: LogicRunId) {
        history.keys.removeAll { it.runExecutionId.logicRunId == logicRunId }
        stableIdHistory.entries.removeAll { it.value.logicRunId == logicRunId }
    }
}
