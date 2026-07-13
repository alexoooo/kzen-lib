package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.service.store.normal.ObjectStableId


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

    /**
     * Subscribe to a change signal (push). Notifications are coalescing-safe, may arrive concurrently, and
     * carry no ordering guarantee — a listener pulls [snapshot] / [history] for state. The returned handle
     * unsubscribes.
     */
    fun observe(listener: () -> Unit): AutoCloseable

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

    /**
     * Replace the run's breakpoint set (run-scoped and volatile — never persisted; cleared with the run).
     * Usable before launch and mid-run. A named boundary ([Execution.checkpoint] `at`) whose element is in
     * the set settles *paused (explicit)* regardless of the in-flight command, and the run drops to paused
     * so every concurrent execution parks at its own next boundary (stop-the-world, mirroring [pause]).
     * Stable-id keyed, so breakpoints survive rename and live-edit migration.
     */
    fun setBreakpoints(ids: Set<ObjectStableId>)

    /** Send an on-demand request to a specific live node and get its response (the pull half of interactivity). */
    fun request(node: NodeId, request: ExecutionRequest): ExecutionResult

    /** The run's history events newer than [sinceSequence], for incremental polling. */
    fun history(sinceSequence: Long): List<TraceEvent>

    /** Suspend until the run reaches a terminal outcome (for background runs and tests). */
    suspend fun await(): Outcome
}
