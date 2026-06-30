package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult


/**
 * The handle a driver / UI holds for one run — the entire run-control surface (logic-spec §4). A run is a
 * plain object that owns all of its own state (engine loop, run state, event log, identity, resources), so
 * nothing is process-global and multiple runs coexist with no shared mutable state.
 *
 * Control is addressed to *this* run; commands are non-blocking (the engine settles asynchronously and the
 * result is observed via [snapshot] / [observe] / [await]).
 */
interface Run {
    /** The latest immutable run-state snapshot (lock-free read). */
    fun snapshot(): RunState

    /** Subscribe to run-state changes (push). The returned handle unsubscribes. */
    fun observe(listener: (RunState) -> Unit): AutoCloseable

    /** Run at full speed to the next halt (terminal, or a pause). Idempotent: the first call starts the run. */
    fun resume()

    /** Settle at the next boundary into a quiescent paused state. */
    fun pause()

    /** Cooperatively cancel the run; it settles to a cancelled outcome, releasing resources. */
    fun cancel()

    /** Advance by exactly one boundary in the given mode. Idempotent: the first call starts the run (paused). */
    fun step(mode: StepMode = StepMode.Into)

    /** Live-togglable: when on, a recoverable failure pauses the run for fix-and-resume instead of ending it. */
    fun pauseOnError(enabled: Boolean)

    /** Send an on-demand request to a specific live node and get its response (the pull half of interactivity). */
    fun request(node: NodeId, request: ExecutionRequest): ExecutionResult

    /** The run's history events newer than [sinceSequence], for incremental polling. */
    fun history(sinceSequence: Long): List<TraceEvent>

    /** Suspend until the run reaches a terminal outcome (for background runs and tests). */
    suspend fun await(): Outcome
}
