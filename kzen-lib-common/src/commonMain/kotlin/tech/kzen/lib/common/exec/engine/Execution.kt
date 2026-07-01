package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * The context a [Logic] runs against — the entire control / trace / resource / host / interact surface a
 * Logic touches, in one place. There is no separate control to poll, no separate trace handle, no separate
 * resource scope, and no separate logic handle: the engine owns the execution tree, so it drives stepping
 * itself and a Logic only declares boundaries with [checkpoint] and records what it does with [emit] / [log].
 */
interface Execution {
    /** This invocation's typed inputs. */
    val inputs: TupleValue

    /**
     * Settle at a boundary — a coherent, observable, pausable point. Suspends while the run is paused or
     * stepping past this frame (per the engine's central decision); throws
     * [kotlin.coroutines.cancellation.CancellationException] when the run is cancelled. This is the *only*
     * control-flow primitive a Logic needs; step-into/over/out are engine policies over the tree.
     */
    suspend fun checkpoint()

    /**
     * Record the current value at [address] (live latest-value-per-address; overwritten as it changes,
     * cleared by a fresh loop iteration). The "push" half of observability.
     */
    fun emit(address: Address, value: ExecutionValue)

    /**
     * Append an immutable event to the run's history timeline (survives loop / iteration resets) — the
     * value-agnostic "film-strip" (a screenshot is just a binary value here).
     */
    fun log(value: ExecutionValue)

    /**
     * Pause this node itself (a breakpoint / pause-step): settle as [NodeStatus.Suspended] with the given
     * reason until the run is resumed, regardless of the current run command.
     */
    suspend fun pauseHere(reason: PauseReason = PauseReason.Explicit)

    /**
     * Run [block] as a **pause-on-error recoverable unit** (logic-spec §4). If it throws (a coroutine cancel
     * excepted — that is never recoverable and always propagates), [onError] is invoked to render the failure
     * (e.g. trace it on the failing element); then, *when pause-on-error is enabled for the run*, this node
     * settles as [NodeStatus.Suspended] with [PauseReason.Error] for inspect / fix + resume, and on resume
     * [block] is re-run — so a still-broken unit pauses anew while a fix (an edit that triggers a migrate, or a
     * transient failure that clears) lets it proceed. When pause-on-error is disabled the failure propagates and
     * the node settles failed.
     *
     * A flavour wraps each recoverable boundary (a Script step, a Flow vertex) in this; the catch / park / retry
     * policy lives once in the engine, so flavours add no pause-on-error control code (only the [onError]
     * rendering and the unit of work). The toggle itself is the run-level [Run.pauseOnError], read live here.
     */
    suspend fun <R> recoverable(onError: (Throwable) -> Unit, block: suspend () -> R): R

    /**
     * Run [child] as a confined sub-execution and return its output. The child runs as a new node under
     * this one — its own trace scope and resource scope — and the engine drives its stepping uniformly
     * (step-over/out cross the boundary). A child failure surfaces here as [LogicFailure]; a child cancel
     * propagates as cancellation. To run children concurrently, wrap [host] calls in structured concurrency
     * (`coroutineScope { launch { host(...) } ... }`).
     *
     * [stableId] is the CHILD's own root identity (used for its frame / migration carry-over). [callerStableId]
     * is the element on THIS side that hosts it — the call-site (a RunStep, a Job worker) — recorded on the
     * child node ([Node.callerStableId]) purely for trace attribution, so a consumer can scope a hosting
     * element's view to the executions it spawned even when several call-sites host the same child document.
     * Null when there is no distinct call-site.
     *
     * [retainTrace] governs the child frame's trace retention (§7 retention-vs-bounding), recorded on the child
     * node ([Node.retainTrace]). True (the default) KEEPS the frame's trace buffer after it closes, so post-run
     * review sees every finished invocation (a Script `RunStep`'s per-iteration screenshot strip depends on
     * this). A long STREAMING host that opens one child per element passes false to opt into eviction of each
     * per-element frame when it settles — bounding a streaming run to its live frames instead of leaking one
     * buffer per element. The engine only records the flag; a trace consumer acts on it at frame close.
     */
    suspend fun host(
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue = TupleValue.empty,
        callerStableId: ObjectStableId? = null,
        retainTrace: Boolean = true
    ): TupleValue

    /**
     * Register a resource scoped to THIS node, disposed when the node settles per [policy]. Re-registering
     * the same [key] replaces the prior closer.
     */
    fun resource(key: String, policy: ClosePolicy, closer: () -> Unit)

    /**
     * Deregister a previously-registered resource [key] (e.g. an explicit closing step disposed it itself),
     * so the auto-disposer never double-fires.
     */
    fun releaseResource(key: String)

    /**
     * Answer on-demand duplex requests addressed to this live node — the "pull" half of interactivity
     * (e.g. "give me your current output slice"). The handler must be safe to call from another thread
     * while this node runs (it typically reads an immutable snapshot the node publishes).
     */
    fun onRequest(handler: (ExecutionRequest) -> ExecutionResult)

    //----------------------------------------------------------------------------------- live-edit migration (§5)
    /**
     * Register the durable run-scoped state this node carries across a **live edit** (pause → edit the
     * definition → resume): an accumulator, a buffered batch, or a detached live resource (an open file, a
     * spawned process). The provider is invoked **once, at the quiescent migration barrier, BEFORE the old
     * execution is torn down** — so it may *detach* a live handle from the node (handing ownership to the
     * returned state) rather than letting teardown close it. The returned value is opaque to the engine and
     * is carried by [the element's stable identity][tech.kzen.lib.common.service.store.normal.ObjectStableId]
     * to the matching node of the rebuilt definition, surfaced there as [restored].
     *
     * Null (the default — no provider registered, or a provider returning null) means nothing migrates: the
     * rebuilt node restarts cleanly with the new definition (the safe best-effort default of spec §5). A
     * returned state that holds a detached resource should be [AutoCloseable]: the engine closes any captured
     * state whose stable id no node of the new definition claims (the **removed-element** case), so a detached
     * handle can't leak.
     */
    fun onCapture(capture: () -> Any?)

    /**
     * The state the predecessor node with **this node's stable identity** captured via [onCapture] in the
     * definition that was edited, or null when this node is **new** (added by the edit) or its predecessor
     * captured nothing. Read once at the start of [Logic.run] to adopt carried-over state; ignoring it
     * discards the predecessor's capture (which is then disposed as an orphan). On a fresh (non-migration)
     * run this is always null.
     */
    val restored: Any?
}
