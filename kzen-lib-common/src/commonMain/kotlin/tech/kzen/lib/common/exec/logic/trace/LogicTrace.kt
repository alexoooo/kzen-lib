package tech.kzen.lib.common.exec.logic.trace

import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicTrace {
    fun mostRecent(
        objectLocation: ObjectLocation
    ): LogicRunExecutionId?


    // Every document that currently holds a retained trace (each run root plus every RunStep sub-logic
    // root), resolved to its current ObjectLocation; ids that no longer resolve (deleted) are omitted.
    fun tracedLocations(): Set<ObjectLocation>


    fun clear(
        objectLocation: ObjectLocation
    ): Boolean


    // Drop every retained trace across all runs (the global "Clear all" — run controls are global).
    fun clearAll()


    fun lookup(
        logicRunExecutionId: LogicRunExecutionId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?


    // Merge every buffer that ran under this run id — the main script plus every RunStep's
    // sub-script execution — into one snapshot, resolving duplicate paths by the latest write
    // (highest sequence). Null when no buffer exists for the run.
    fun lookupRun(
        logicRunId: LogicRunId,
        logicTraceQuery: LogicTraceQuery
    ): LogicTraceSnapshot?


    // The run's append-only event history (across all its executions), ordered by sequence, returning
    // only events newer than sinceSequence so callers can poll incrementally. Survives loop clears.
    fun lookupRunHistory(
        logicRunId: LogicRunId,
        sinceSequence: Long
    ): List<LogicTraceEvent>
}
