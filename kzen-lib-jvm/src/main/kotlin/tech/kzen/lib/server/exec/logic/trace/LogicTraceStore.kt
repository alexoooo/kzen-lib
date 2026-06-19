package tech.kzen.lib.server.exec.logic.trace

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.LogicTrace
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEntry
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock


class LogicTraceStore(
    private val objectStableMapper: ObjectStableMapper
):
    LogicTrace
{
    //-----------------------------------------------------------------------------------------------------------------
    private data class RunExecution(
        val runExecutionId: LogicRunExecutionId
    )


    private class TraceBuffer(
        // The execution's root object (the originalObjectLocation it was opened for), so history
        // events can be attributed to / labelled by their sub-logic without re-deriving it.
        val rootStableId: ObjectStableId
    ) {
        val values = ConcurrentHashMap<LogicTracePath, LogicTraceEntry>()

        // Append-only history; NOT cleared by clearAll, so loop iterations accumulate.
        val events = CopyOnWriteArrayList<LogicTraceEvent>()

        val callbacks = CopyOnWriteArrayList<(LogicTraceQuery) -> Unit>()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val history = ConcurrentHashMap<RunExecution, TraceBuffer>()

    // The run-scope entry point is keyed by its stable id (not its current ObjectLocation) so the
    // "most recent run" / "Reset" association survives a rename of the script's root document.
    private val stableIdHistory = ConcurrentHashMap<ObjectStableId, LogicRunExecutionId>()

    // Stamp every write at the single choke point: wall-clock for the human-facing "when", and a
    // process-global monotonic sequence for a total order across all buffers (the run-merge tiebreaker).
    private val clock = Clock.System
    private val sequence = AtomicLong(0L)


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
                buffer.values[logicTracePath] = LogicTraceEntry(
                    executionValue, clock.now(), sequence.incrementAndGet())
            }

            override fun append(objectStableId: ObjectStableId, value: ExecutionValue) {
                buffer.events.add(LogicTraceEvent(
                    runExecutionId.logicExecutionId,
                    buffer.rootStableId,
                    objectStableId,
                    sequence.incrementAndGet(),
                    clock.now(),
                    value))
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
        if (previous != null && previous != runExecutionId) {
            if (previous.logicRunId != runExecutionId.logicRunId) {
                evict(previous.logicRunId)
            }
            else {
                // Same run, same logic re-opened under a new execution id — i.e. a sub-logic invoked
                // again (a loop iteration / a RunStep re-run). Drop the previous invocation's live
                // per-path values so the whole-run merge (lookupRun) shows this invocation starting
                // fresh instead of the prior iteration's finished state. Its append-only events are
                // kept — the retained film-strip history must survive across loop iterations.
                history[RunExecution(previous)]?.values?.clear()
            }
        }
        stableIdHistory[stableId] = runExecutionId

        val runExecution = RunExecution(runExecutionId)
        return history.getOrPut(runExecution) { TraceBuffer(stableId) }
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

        val retainedValues = mutableMapOf<LogicTracePath, LogicTraceEntry>()
        for ((storedPath, value) in buffer.values) {
            val retainedPath = retainStoredPath(storedPath)
                ?: continue
            if (logicTraceQuery.match(retainedPath)) {
                retainedValues[retainedPath] = value
            }
        }

        return LogicTraceSnapshot(retainedValues)
    }


    // Whole-run view: a RunStep's sub-script runs under a fresh LogicExecutionId but the parent's
    // LogicRunId, so its frames live in a separate buffer. Merge every buffer of the run into one
    // snapshot; on a path collision (a sub-script invoked more than once) keep the latest write.
    override fun lookupRun(
        logicRunId: LogicRunId,
        logicTraceQuery: LogicTraceQuery
    ):
        LogicTraceSnapshot?
    {
        val buffers = history
            .filterKeys { it.runExecutionId.logicRunId == logicRunId }
            .values
        if (buffers.isEmpty()) {
            return null
        }

        val merged = mutableMapOf<LogicTracePath, LogicTraceEntry>()
        for (buffer in buffers) {
            buffer.callbacks.forEach { it(logicTraceQuery) }

            for ((storedPath, entry) in buffer.values) {
                val retainedPath = retainStoredPath(storedPath)
                    ?: continue
                if (!logicTraceQuery.match(retainedPath)) {
                    continue
                }
                val existing = merged[retainedPath]
                if (existing == null || entry.sequence > existing.sequence) {
                    merged[retainedPath] = entry
                }
            }
        }

        return LogicTraceSnapshot(merged)
    }


    override fun lookupRunHistory(
        logicRunId: LogicRunId,
        sinceSequence: Long
    ): List<LogicTraceEvent> {
        return history
            .filterKeys { it.runExecutionId.logicRunId == logicRunId }
            .values
            .flatMap { it.events }
            .filter { it.sequence > sinceSequence }
            .sortedBy { it.sequence }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun evict(logicRunId: LogicRunId) {
        history.keys.removeAll { it.runExecutionId.logicRunId == logicRunId }
        stableIdHistory.entries.removeAll { it.value.logicRunId == logicRunId }
    }
}
