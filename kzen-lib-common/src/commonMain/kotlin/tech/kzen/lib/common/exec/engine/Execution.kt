package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.ContextFamily
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
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
     * Run [child] as a confined sub-execution and return its output: a new node under this one, with its own
     * trace scope and resource scope, stepped uniformly by the engine (step-over/out cross the boundary).
     * A child failure surfaces here as [LogicFailure]; a child cancel propagates as cancellation. To run
     * children concurrently, wrap [host] calls in structured concurrency
     * (`coroutineScope { launch { host(...) } ... }`) — and pass [contextBarrier].
     *
     * [stableId] is the CHILD's own root identity (used for its frame / migration carry-over). [callerStableId]
     * is the element on THIS side that hosts it — the call-site (a RunStep, a Job worker), recorded on the
     * child node ([Node.callerStableId]). It is **load-bearing frame addressing**, not a cosmetic label:
     * invocation identity for migration adoption ([restored]), the per-hop path unit of a repositioning
     * request ([MoveTarget.callSitePath]), and trace attribution. Null when this host names no distinct
     * call-site — null never matches a path hop (it is not a wildcard), so a frame reached through such a hop
     * cannot be path-addressed.
     *
     * [retainTrace] governs the child frame's trace retention after it closes, recorded on the child node
     * ([Node.retainTrace]); a long streaming host passes false to bound the run to its live frames. The engine
     * only records the flag; a trace consumer acts on it at frame close. See logic-spec §7
     * retention-vs-bounding.
     *
     * [initialBindings] bootstrap the child's ambient context per call: plain borrows (see [InitialBinding])
     * installed on the child frame under the same lock that mints it, so the child never observes a
     * half-bootstrapped frame. Listed order is put order, so a duplicated key keeps the last entry. Semantics
     * (borrow vs own, live-edit re-supply, supersession): logic-spec §6 "call-site bootstrap"; the
     * bootstrap-vs-adoption ordering is pinned by
     * `RunEngineContextTest.anAdoptedLocalBindingSupersedesTheSameKeyBootstrapValueOnRebuild`.
     *
     * [contextBarrier] makes the child frame a wall for OUTWARD context writes while leaving INWARD reads
     * transparent. **Pass it whenever this host call is one of several launched concurrently** — the engine
     * cannot infer that, because "am I inside a `coroutineScope` with siblings" is not something a frame can
     * see. Sequential composition (Script, Flow) must NOT pass it — the open → use → close split across
     * sibling sub-documents is built on exactly the upward reach it removes. Full semantics and rationale:
     * logic-spec §6 concurrent-frames rule.
     */
    suspend fun host(
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue = TupleValue.empty,
        callerStableId: ObjectStableId? = null,
        retainTrace: Boolean = true,
        initialBindings: List<InitialBinding> = listOf(),
        contextBarrier: Boolean = false
    ): TupleValue

    //----------------------------------------------------------------------------------- resources & exports (§6)
    /**
     * Declare that THIS node **exports** what [selector] covers to its host: a binding registered here — or
     * anywhere beneath this node — under a covered key climbs PAST this frame to its host, and keeps climbing
     * while each frame in turn exports it. Ownership is OFFERED by the provider (a `return` move), never
     * claimed by an ancestor; what a frame does not export is private to it, so an un-exported resource is
     * disposed at the settle of the frame that opened it.
     *
     * [ExportSelector.Exact] and [ExportSelector.Family] are a real distinction here, not a convenience: a
     * declaration naming one qualified member must not move its siblings. See [ExportSelector].
     *
     * Call at [Logic.run] start, before hosting children and before any local step opens a resource, so the
     * chain is complete before anything can climb it. Idempotent, and free to re-run: a live-edit migration
     * rebuilds the tree and re-runs each [Logic.run], which re-declares. An already-bound resource keeps the
     * owner it bound to (ownership is fixed at bind time), so adding or removing a declaration by editing
     * affects only subsequent opens.
     */
    fun declareExport(selector: ExportSelector)

    @Deprecated(
        "Declare an ExportSelector — a bare family and a qualified member are different claims",
        ReplaceWith("declareExport(ExportSelector.parse(key))"))
    fun declareExport(key: String)

    /**
     * Read the ambient binding at [key], searching this node's ancestor chain (self → parent → … → root) and
     * stopping at the first frame that holds it — so a nearer binding shadows a farther one. This is the §6
     * "resource inheritance along the host chain" read affordance: a hosted child borrows the handle its host
     * (or any ancestor) bound; ownership and disposal stay with the registering frame, and the reader must not
     * dispose what it borrows.
     *
     * [BindingLookup.Missing] and [BindingLookup.Present] with a null value are DIFFERENT answers: presence is
     * registration-existence, not value-non-nullness, so a nullable Context that legitimately binds null is
     * distinguishable from one nothing bound.
     */
    fun binding(key: ContextKey): BindingLookup

    /** Is a binding live at exactly [key] anywhere on this node's ancestor chain? */
    fun hasBinding(key: ContextKey): Boolean

    /**
     * Bind [value] at [key], on the frame the export chain resolves ([declareExport]) — this node when nothing
     * matching is exported. One binding per key per frame, so a nearer one shadows a farther one and re-binding
     * the same key on the same frame **supersedes**: the displaced binding's [disposal], if it had one, runs
     * exactly once, there and then, because a key resolves to one binding and nothing could reach the displaced
     * one again. Without that a loop re-binding a browser each iteration leaks every iteration but the last.
     *
     * [disposal] is the deliberate composition of the two features, and it is a PARAMETER rather than a
     * separate call for a structural reason: an attached teardown cannot be given a frame different from its
     * binding's, so a descendant can never read a handle that was already closed. Pass null — the default — for
     * an ordinary ambient value or an explicit borrow, which says nothing about tearing anything down.
     * For teardown with no value to name, use [onSettle].
     */
    fun bind(key: ContextKey, value: Any?, disposal: FrameDisposal? = null)

    /**
     * Remove the nearest binding at [key] on this node's ancestor chain — so a binding resting on an ancestor
     * frame it was exported to can be released from a descendant — and invoke its attached disposal, at most
     * once. A plain or borrowed binding has no disposal, so release degenerates safely to unbinding the name.
     *
     * This is why the user-facing verb stays "release" rather than "unbind": the operation does what the word
     * promises. A remove-WITHOUT-dispose operation, if one is ever wanted, must be separately named; it must
     * not hide behind this one.
     */
    fun releaseBinding(key: ContextKey)

    /**
     * Register anonymous teardown against THIS frame: [closer] runs when this node settles, per [policy]. No
     * key, no namespace entry, no lookup — "delete this file / kill this process when I finish", which is the
     * common case and has no name worth inventing.
     *
     * Always frame-local: an anonymous registration never climbs an export chain, because handing upward
     * something nobody can name is meaningless. A closer that must outlive its frame belongs to a *binding*,
     * and [bind]'s `disposal` then carries it exactly as far as the export chain carries the name.
     *
     * Invoked at most once, and — having no name — with no early-release operation; a use case that needs one
     * wants a managed binding.
     */
    fun onSettle(policy: SettleDisposalPolicy, closer: () -> Unit)

    /**
     * Is SOME member of [family] live on this node's ancestor chain — the bare family key or any qualifier of
     * it? Deliberately family-granular: a computed qualifier is a step parameter, so a declaration-driven gate
     * cannot know which member a reader will end up asking for, and a qualifier mismatch surfaces at the read
     * instead. A DECLARED qualifier should gate with [hasBinding], which answers the exact question.
     */
    fun hasBindingInFamily(family: ContextFamily): Boolean

    //--------------------------------------------------------------------------------- raw string interop (§6)
    // The plain-string layer beneath the typed API above, and a SUPPORTED one rather than debt. Keys are a
    // global namespace (§6): `ContextKey.asString` / `parse` are exact inverses, so a raw caller and a typed
    // one naming `sut` address one registration — which is the whole point, and what lets a typed step open
    // the browser a raw step then drives, or a plugin interoperate with a first-party Context without
    // declaring one. What a caller gives up by staying here is DECLARATION, not correctness: nothing raw is
    // visible to the static analysis, so a raw open leaves a downstream typed `uses` unsatisfiable. Prefer the
    // typed API whenever the key is known at authoring time; this exists for when it genuinely is not.
    //
    // Strict to WRITE, permissive to ADDRESS, deliberately. Registering under a string no key could be
    // spelled as is a caller bug with no sensible silent outcome, so [resource] throws. Reading or releasing
    // such a string addresses nothing — every key in a registry got there through `parse` — so answering
    // "nothing is bound there" is more truthful than throwing at a reader, and those two go through
    // `ContextKey.parseOrNull`.
    /**
     * Register a resource under [key], owned by the furthest frame on this node's ancestor chain (self →
     * parent → … → root) reachable through an **unbroken chain of [declareExport] declarations** — matched on
     * the exact [key] or the family before the first `':'` — resting at the first frame that does not export
     * it, and **falling back to THIS node** when this node exports nothing matching. It is disposed when its
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
     *
     * The typed equivalent splits the fused name-and-teardown in two: `bind(key, value, disposal)` is this
     * call's composed form, [bind] alone binds a value that owns nothing, and [onSettle] registers a teardown
     * worth no name. Throws `IllegalArgumentException` for a [key] that is not a spellable `ContextKey`.
     */
    fun resource(
        key: String,
        policy: ClosePolicy,
        value: Any? = null,
        closer: () -> Unit)

    /**
     * Read the live handle stored with a resource registration (the [resource] `value`), searching this
     * node's ancestor chain (self → parent → … → root); null when no live registration holds the [key], when
     * the registration holds null, or when [key] is not a spellable `ContextKey`.
     *
     * Lossy by construction — it collapses "nothing is bound" onto "a binding holds null", which a Context
     * with a nullable value contract can produce deliberately. A caller that needs those apart wants [binding]
     * and its `BindingLookup`; a caller reaching for an arbitrary runtime string usually cannot act on the
     * difference anyway.
     */
    fun resourceValue(key: String): Any?

    /**
     * True when a live registration exists on this node's ancestor chain whose key is [family] itself or
     * `"[family]:<qualifier>"`.
     *
     * A plain-string family is exactly the hazard [ContextFamily] exists to close: passing a fully-qualified
     * key here degrades the gate to an exact-key check with no diagnostic — true only if a registration exists
     * under that whole string, silently false for the family it looks like it is asking about. The behaviour
     * is preserved as-is for callers still on the string form; [hasBindingInFamily] is the fix.
     *
     * Unlike the raw interop layer above, this stays deprecated: it is not a lossless string spelling of a
     * typed operation but a *different, wrong* one, and no caller has ever needed the wrong answer.
     */
    @Deprecated(
        "A fully-qualified key silently degrades this to an exact-key check",
        ReplaceWith("hasBindingInFamily(ContextFamily(family))"))
    fun hasResourceInFamily(family: String): Boolean

    /**
     * Deregister a previously-registered resource [key] (e.g. an explicit closing step disposed it itself),
     * so the auto-disposer never double-fires. Searches this node's ancestor chain (self → parent → … → root),
     * so a resource resting on an ancestor frame it was exported to can be released from a descendant. A [key]
     * that is not a spellable `ContextKey` addresses nothing, so it is a no-op.
     *
     * Removes WITHOUT invoking the registration's closer — the operation exists for a caller that already tore
     * the resource down itself, and it has no typed equivalent because the typed API deliberately offers only
     * [releaseBinding], which removes the name AND runs the teardown attached to it. The two are different
     * operations, not two spellings of one; do not read this as the string form of [releaseBinding].
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
     * rebuilt walk. Any Logic that does not support repositioning, or in whose structure the id does not
     * resolve, MUST ignore it, in which case the rebuild is an ordinary migrate parked at the existing
     * frontier.
     *
     * Delivered to exactly ONE frame of the rebuilt tree: the one the request's
     * [call-site path][MoveTarget.callSitePath] names. Every other frame reads null here — a frame on the
     * way to the addressed one reads [moveDescendCallSite] instead, and a frame off the path reads null on
     * both. That precision is what holds under recursion, where the same id resolves in several live frames
     * at once and only the addressed one may move.
     *
     * Unlike [restored], reading is not a claim: a frame may read it repeatedly during one barrier's
     * rebuild. Non-null only on a rebuilt tree; null on a fresh run.
     */
    val moveTarget: ObjectStableId?

    /**
     * The call-site this frame must DESCEND THROUGH rather than park at — surfaced to a TRANSIT frame on the
     * path to the frame a move request addresses, in place of a target of its own. The frame runs to that
     * call-site with its own boundary SUPPRESSED, so the paused rebuild does not park at the hosting element,
     * and then hosts it: the descent obligation that carries the move down to the addressed frame. Null for
     * every frame that is not a transit frame on this barrier's path — including the addressed frame itself,
     * which reads [moveTarget].
     */
    val moveDescendCallSite: ObjectStableId?

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
