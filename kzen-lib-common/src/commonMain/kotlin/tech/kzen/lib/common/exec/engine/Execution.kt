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
     *
     * [at] optionally names the element this boundary settles on (a Script step, a Flow vertex). The engine
     * records it — whether or not the boundary parks — as the node's current position ([Node.position]:
     * the last *named* boundary reached), surfaced in run snapshots so consumers need no reserved trace
     * markers for "next to run". An anonymous boundary (null, e.g. a step's internal pausability
     * checkpoint) leaves the recorded position unchanged; position clears only when the node settles.
     */
    suspend fun checkpoint(at: ObjectStableId? = null)

    /**
     * Record the current value at [address] (live latest-value-per-address; overwritten as it changes,
     * cleared by a fresh loop iteration via [resetEmitted]). The "push" half of observability.
     *
     * [retain] = true (the default) also appends the write to the run's append-only history — the
     * durable event log the live view is normally a projection of. [retain] = false makes the write
     * **transient**: it updates the live latest-value view (and notifies observers) but is NOT appended
     * to history, so a high-churn progress signal (a throttled row count, a "running" marker) drives the
     * live display without unbounded history growth. A transient emit is still visible in the live and
     * whole-run-merged views; only the append-only history / film-strip omits it.
     */
    fun emit(address: Address, value: ExecutionValue, retain: Boolean = true)

    /**
     * Append an immutable event to the run's history timeline (survives loop / iteration resets) — the
     * value-agnostic "film-strip" (a screenshot is just a binary value here).
     */
    fun log(value: ExecutionValue)

    /**
     * Reset the live (latest-value-per-address) trace of a re-running scope — the "resettable" half of the
     * §7 live view; the append-only history ([log]) is untouched, so the film-strip of every prior pass
     * survives. Removes THIS node's live values at [addresses], and signals that hosted child invocations
     * launched from any of [callSites] — transitively including their own hosted descendants — are
     * superseded by the coming fresh pass, so a trace consumer clears their retained live values likewise
     * (a fresh iteration presents a fresh trace instead of the previous iteration's finished one).
     * An element that re-runs its nested elements (a loop at each iteration boundary) calls this alongside
     * [discardCaptured] — same element set: addresses for the values the elements emitted, call-sites for
     * the invocations they hosted. No-op with empty arguments.
     */
    fun resetEmitted(addresses: Collection<Address>, callSites: Collection<ObjectStableId> = emptyList())

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
     * Run a BLOCKING [block] off the engine's fixed dispatcher pool on a per-engine elastic pool — freeing the
     * engine thread while it blocks, yet still counted as in-flight, so a spine parked inside [blocking] reads
     * as BUSY to the quiescence barrier (never falsely quiescent), exactly as a pending
     * [delay][kotlinx.coroutines.delay] does. Adopt it for blocking third-party calls (Selenium round-trips,
     * JDBC, large file reads) that would otherwise hold one of the fixed engine threads and, in enough
     * concurrent spines, starve the pool and stall pause / step / migrate. This is the mechanism behind
     * logic-spec §2's "blocking work must remain visible to the runtime so it can tell 'busy' from 'idle'".
     *
     * [block] MUST be interrupt-responsive: engine cancel (and the migrate barrier) converge a
     * parked-in-[blocking] spine by INTERRUPTING its worker thread, surfaced back as a
     * [kotlin.coroutines.cancellation.CancellationException] — an uninterruptible block cannot be cancelled and
     * holds a pooled thread until it returns on its own.
     */
    suspend fun <R> blocking(block: () -> R): R

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

    //----------------------------------------------------------------------------------- resources & slots (§6)
    /**
     * Declare that THIS node owns a **context slot** for [key]: any descendant registering a resource under
     * [key] — or under a qualified `"[key]:<qualifier>"` of the same family — binds HERE rather than on
     * itself, so disposal follows this node's settle. Ownership is the ancestor's own declaration, not the
     * opener's unilateral choice.
     *
     * Call at [Logic.run] start, before hosting children, so a parent has declared before any descendant can
     * open. Idempotent, and free to re-run: a live-edit migration rebuilds the tree and re-runs each
     * [Logic.run], which re-declares. An already-bound resource keeps the owner it bound to (ownership is
     * fixed at bind time), so removing a declaration by editing affects only subsequent opens.
     */
    fun declareSlot(key: String)

    /**
     * Register a resource under [key], owned by the nearest node on this node's ancestor chain (self →
     * parent → … → root) that [declareSlot]s a matching slot — exact [key], or the family before the first
     * `':'` — and **falling back to THIS node** when no ancestor declares one. It is disposed when its
     * owning node settles, per [policy].
     *
     * Re-registering the same [key] **supersedes**: the displaced registration's [closer] is run, because a
     * key resolves to exactly one registration, so nothing could ever reach the displaced one again. Without
     * it a loop that re-opens the same resource each iteration leaks every iteration but the last.
     *
     * CLOSER CONTRACT. A [closer] must dispose the handle it CAPTURED, never re-resolve its target by name
     * from a registry — a superseded closer runs *after* the replacement is registered, so a
     * `close(byName)` closer would tear down the replacement instead of the thing it was registered for. It
     * must also tolerate running twice (an explicit closing step that already disposed the resource should
     * [releaseResource] it, but a closer that throws or double-closes is only swallowed, not prevented).
     *
     * [value] optionally stores the live handle with the registration, readable via [resourceValue]; it
     * travels with the registration across a live-edit migration (§5), so an open resource survives an edit
     * with its owning frame's stable identity.
     */
    fun resource(
        key: String,
        policy: ClosePolicy,
        value: Any? = null,
        closer: () -> Unit)

    /**
     * Read the live handle stored with a resource registration (the [resource] `value`), searching this
     * node's ancestor chain (self → parent → … → root); null when no live registration holds the [key] (or
     * it registered no value). This is the §6 "resource inheritance along the host chain" read affordance:
     * a hosted child borrows the handle its host (or any ancestor) opened — ownership and disposal stay
     * with the registering frame; the reader must not dispose what it borrows.
     */
    fun resourceValue(key: String): Any?

    /**
     * True when a live registration exists on this node's ancestor chain whose key is [family] itself or
     * `"[family]:<qualifier>"`. Deliberately **family-level**: it answers "is SOME browser / SOME sut open",
     * never "is `sut:formula-error` open" — a qualifier is a step parameter and may be computed, so a
     * declaration-driven check cannot know which one a reader wants. Used by a flavour's uniform
     * requirement gate; a qualifier mismatch still surfaces at read.
     */
    fun hasResourceInFamily(family: String): Boolean

    /**
     * Deregister a previously-registered resource [key] (e.g. an explicit closing step disposed it itself),
     * so the auto-disposer never double-fires. Searches this node's ancestor chain (self → parent → … → root),
     * so a resource bound to a slot-declaring ancestor can be released from a descendant.
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
     * The captured value should be a **self-contained value type** — a plain data holder that owns the state
     * it carries (copy mutable collections, don't hand out a live view onto engine internals) — since it
     * outlives the node that produced it and is adopted later on the rebuilt tree (read back via [restored] /
     * [restoredAs]).
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
     *
     * Adoption carries **invocation identity**: a hosted child's capture is delivered only to a node hosted
     * from the **same call-site** (the [host] `callerStableId`) as the captured invocation — so when several
     * call-sites host the same child document, one's mid-flight state can never leak into another's fresh
     * invocation. When several invocations of one hosted document share a stable id at the barrier (a loop's
     * retained settled iterations plus the live one), the **live frame's capture wins** — a settled frame's
     * capture still carries when it is the only one, so a flavour that relaunches completed elements (a Job
     * worker) adopts the "done" state instead of redoing the work. A host that re-runs call-sites live uses
     * [discardCaptured] so their fresh invocations start clean.
     */
    val restored: Any?

    /**
     * Advisory one-shot repositioning hint set by the driver at the migration barrier (§5). A [Logic]
     * whose structure resolves this id MAY interpret it when adopting [restored] — repositioning the
     * rebuilt walk. Any Logic that does not support repositioning, or a hosted child in whose structure
     * the id does not resolve, MUST ignore it, in which case the rebuild is an ordinary migrate parked at
     * the existing frontier. Unlike [restored], reading is not a claim: the root and hosted children may
     * all read it during one barrier's rebuild. Non-null only on a rebuilt tree; null on a fresh run.
     */
    val moveTarget: ObjectStableId?

    /**
     * The stable identities the edit REMOVED, as reported by the driver at the migration barrier (§5
     * "an element the edit removed is disposed"). Empty on a fresh run and on a barrier that removed nothing.
     *
     * The engine applies this to what IT keys by stable id — captures, lifted resources, breakpoints — so a
     * [Logic] only needs it for state of its own that it keys the same way INSIDE one capture (a Script's
     * per-step outcomes all ride the root's [onCapture]). Without it, "the same element" and "a different
     * element created at the removed one's address" are indistinguishable, and the new element would silently
     * inherit the removed one's state. Like [moveTarget], reading is not a claim: every node of the rebuilt
     * tree may read it during one barrier's rebuild.
     */
    val removedStableIds: Set<ObjectStableId>

    /**
     * Discard captured migration state belonging to child invocations hosted from any of [callSites] —
     * transitively including THEIR hosted descendants' captures. This is the invocation-identity signal only
     * the flavour has: an element that re-runs its nested elements live (a loop resetting for its next
     * iteration, or restarting) calls this so a FRESH child invocation from the same call-site starts clean
     * instead of adopting the pre-edit (abandoned) invocation's state. A discarded state that was never
     * claimed via [restored] is closed if [AutoCloseable] (as an orphan would be); an already-claimed one is
     * only dropped from the register — the claimant owns it. No-op outside a migration window.
     */
    fun discardCaptured(callSites: Collection<ObjectStableId>)
}


/**
 * Type-safe [Execution.restored]: the predecessor's captured migration state as [T], or null when there is
 * none or it captured a different type — instead of a `ClassCastException` from an unchecked
 * `restored as? T` at each seam. Reading claims the capture exactly as [Execution.restored] does, so call it
 * once. For a generic [T] (e.g. a `Map<..>`) the runtime check is by erased class, like any reified `as?`.
 */
inline fun <reified T> Execution.restoredAs(): T? =
    restored as? T
