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
     * Run [child] as a confined sub-execution and return its output. The child runs as a new node under
     * this one — its own trace scope and resource scope — and the engine drives its stepping uniformly
     * (step-over/out cross the boundary). A child failure surfaces here as [LogicFailure]; a child cancel
     * propagates as cancellation. To run children concurrently, wrap [host] calls in structured concurrency
     * (`coroutineScope { launch { host(...) } ... }`).
     */
    suspend fun host(
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue = TupleValue.empty
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
}
