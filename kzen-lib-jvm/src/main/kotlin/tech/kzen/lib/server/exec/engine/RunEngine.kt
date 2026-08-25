package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.ContextFamily
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.context.RetainedBinding
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.MoveTarget
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeId
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.Run
import tech.kzen.lib.common.exec.engine.RunState
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.engine.TraceEvent
import tech.kzen.lib.common.exec.engine.TraceReset
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.util.ExceptionUtils


/**
 * The single-writer execution engine — the use-case-agnostic core of the Logic framework.
 *
 * One [RunEngine] instance owns one run and *all* of its state (no process-global singletons): the
 * execution-tree runtime, the event log, the run command, identity counter, and resource registrations.
 * Every mutation is serialized under a single [lock] (the "single writer"), which assigns the deterministic
 * fold [sequence] and marks the run [dirty]; the immutable [RunState] snapshot is rebuilt lazily on the next
 * [snapshot] read and cached in the [published] volatile — so the emit/log hot path never builds the tree,
 * concurrent readers (UI, tests) see a coherent whole-tree value, and parallel worker coroutines (run on the
 * [CountingDispatcher]) never touch shared state directly; they only emit through the [Execution] handed to
 * them, which routes back into this single writer. Observers receive a payload-free change signal and pull
 * [snapshot] / [history] for state.
 *
 * Stepping (into / over / out) is computed centrally from the tree's depth this engine owns, so flavours
 * add no stepping code — a Logic only declares boundaries with [Execution.checkpoint].
 */
class RunEngine(
    rootLogic: Logic,
    private val rootStableId: ObjectStableId,
    private val rootInputs: TupleValue = TupleValue.empty,
    threads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
): Run, AutoCloseable {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(RunEngine::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private sealed interface Command {
        data object Running: Command
        data object Paused: Command
        data class SteppingOver(val limit: Int): Command
        data class SteppingOut(val limit: Int): Command
    }


    private class Parked(
        val deferred: CompletableDeferred<Unit>,
        val depth: Int
    )


    // An ambient binding: a value in scope under a key, plus — only when the value really is a resource — the
    // teardown that travels with it. The two are separate features composed here (logic-spec §6): a plain
    // ambient value carries no disposal, and a teardown with nothing to name is registered anonymously in
    // [NodeRuntime.settleDisposals] instead.
    private class Binding(
        val value: Any?,
        val disposal: FrameDisposal?,
        // Installed by a call site as [InitialBinding] rather than bound by the frame itself — a BORROW, which
        // the [migrate] barrier must not lift: the rebuilt caller re-supplies it from whatever its sources hold
        // now, and carrying the old one would pin the callee to a subject the edited caller no longer names.
        // A flag on the entry rather than a set beside the map, deliberately: a later [bind] under the same key
        // replaces the entry and so clears the marking on its own, which is exactly right — the frame has now
        // bound its own value there — and no separate bookkeeping can drift out of sync with the map. That
        // self-clearing holds only while the bind lands on THIS frame, which is why [bind] additionally
        // supersedes borrows the export climb travelled past ([supersedeBorrowsBelowOwner]).
        val bootstrap: Boolean = false
    )


    // What one torn-down node's stable id carries across the [migrate] barrier: both registries, so an
    // anonymous cleanup survives an edit exactly as a named binding does.
    private class LiftedRegistrations(
        val bindings: LinkedHashMap<ContextKey, Binding>,
        val settleDisposals: List<FrameDisposal>
    )


    // A managed binding a settled frame kept rather than disposed, held so it stays findable and closeable
    // (see [retainedBindings]).
    private class Retained(
        val nodeId: NodeId,
        val key: ContextKey,
        val binding: Binding
    )


    private class NodeRuntime(
        val id: NodeId,
        val stableId: ObjectStableId,
        val depth: Int,
        // The node that hosted this one (one level up); null for the root. The ancestor chain it forms is
        // what [exportOwnerOf] climbs to resolve export-chain ownership, and what the resource read / release walks
        // follow. Mutable only for a settled frame carried across the [migrate] barrier, which is
        // re-attached to the rebuilt node that shares its host's stable id (see [adoptRetiredFrames]) — a
        // stale id here would break the next barrier's parent-stable-id lookup.
        var parentId: NodeId?,
        val inputs: TupleValue,
        // The element that hosted this node (a RunStep / Job worker), carried to [Node.callerStableId] for
        // trace attribution; null for the root and for a host that named no distinct caller.
        val callerStableId: ObjectStableId? = null,
        // Whether this node's trace buffer is retained after the frame closes, carried to [Node.retainTrace]
        // (§7 retention-vs-bounding); false makes the engine compact the frame out of [nodes] / the snapshot
        // tree on settle (see [settleNode]), and the frame-close signal ([observeFrames]) lets a trace
        // consumer evict its buffer likewise. Always true for the root.
        val retainTrace: Boolean = true,
        // A wall for OUTWARD context writes, transparent to INWARD reads (logic-spec §6) — what a flavour that
        // hosts several children CONCURRENTLY declares on each of them, so their bindings cannot meet. §6's
        // supersede rule was specified for a sequential host chain: two siblings exporting one key would
        // otherwise collapse onto a single slot in the shared parent, where the second bind displaces the first
        // and runs its closer while that sibling is still live. The lock makes that safe (one claim, no leak,
        // no double close) but not MEANINGFUL — the winner is whichever coroutine got there first.
        // Four walks change at this frame, and only these four; every one of them moves a registration UP or
        // destroys one above:
        //   - [exportOwnerOf] stops here, so a bind at or below this frame can never rest above it
        //   - [declareExport] is an ERROR here rather than a silent no-op, so the restriction is visible where
        //     it is violated instead of surfacing later as a value that mysteriously stayed local
        //   - [removeNearestBinding] stops here, so a sibling cannot release a shared ancestor's binding
        //   - [settleFrame]'s `manual` PROMOTION retains instead of handing up (there is nowhere to hand to)
        // The read walks ([bindingOf], [anyBindingOnChain]) are deliberately untouched: a barrier child still
        // inherits everything its ancestors bound, call-site borrows included. Reading up is not a race.
        val contextBarrier: Boolean = false
    ) {
        var status: NodeStatus = NodeStatus.Running
        // Last named boundary reached, carried to [Node.position]; anonymous checkpoints leave it unchanged.
        // Starts null on a migrate rebuild too — re-established when the rebuilt spine re-parks at its boundary.
        var position: ObjectStableId? = null
        // The call-site hops still remaining from THIS frame to the frame a [MoveTarget] addresses. Null and
        // empty are DIFFERENT states: null = not addressed (both move surfaces read null); empty = this IS the
        // addressed frame (it reads [Execution.moveTarget]); non-empty = a transit frame, whose first entry is
        // the call-site it must descend through ([Execution.moveDescendCallSite]). Collapsing the two would
        // hand the target to every unaddressed frame — precisely what path addressing exists to prevent.
        var moveSuffix: List<ObjectStableId>? = null
        val live = LinkedHashMap<Address, ExecutionValue>()
        // Parallel to [live], carried to [Node.liveSequence]: the write sequence of each live entry.
        val liveSequence = LinkedHashMap<Address, Long>()
        val children = ArrayList<NodeId>()
        val bindings = LinkedHashMap<ContextKey, Binding>()
        // Anonymous teardown registered against THIS frame ([Execution.onSettle]) — no key, no lookup, and it
        // never climbs an export chain, because handing upward something nobody can name is meaningless.
        val settleDisposals = ArrayList<FrameDisposal>()
        // Contexts this node EXPORTS to its host ([Execution.declareExport]): a registration under a key one
        // of these selectors covers climbs PAST this node to its parent, and keeps climbing while each frame
        // in turn exports it. A key no selector covers makes this node the resting frame, so an un-exported
        // resource is private to the frame that opened it.
        // Deliberately NOT lifted across the [migrate] barrier: the rebuilt tree re-runs each [Logic.run],
        // which re-declares. Already-bound resources ARE lifted, keyed by their owner's stable id, so a
        // resource keeps the owner it bound to regardless of what the edit did to the declarations.
        val exports = LinkedHashSet<ExportSelector>()
        var requestHandler: ((ExecutionRequest) -> ExecutionResult)? = null
        var captureProvider: (() -> Any?)? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val lock = Any()
    private val dispatcher = CountingDispatcher(threads)
    // Elastic pool for [Execution.blocking]: blocking third-party calls run here (via runInterruptible) rather
    // than holding one of the fixed [dispatcher] threads, while the [CountingDispatcher] in-flight hold keeps
    // the run non-quiescent. Owned by this engine; closed in [shutdown] / [dispose].
    private val elasticDispatcher = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kzen-engine-blocking").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var scope = CoroutineScope(dispatcher + SupervisorJob())

    private val nodes = HashMap<NodeId, NodeRuntime>()
    // Each hosted child's compiled Logic; the root's is [liveRootLogic], which [migrate] can replace.
    private val childLogic = HashMap<NodeId, Logic>()
    private val parked = HashMap<NodeId, Parked>()
    private val history = ArrayList<TraceEvent>()
    private val observers = ArrayList<() -> Unit>()
    private val frameObservers = ArrayList<(Node) -> Unit>()
    private val resetObservers = ArrayList<(TraceReset) -> Unit>()
    private val terminal = CompletableDeferred<Outcome>()

    // One node's captured migration state plus the invocation identity needed to deliver it correctly: the
    // call-site that hosted the captured invocation (null for the root / an anonymous host) and the hosting
    // node's stable id (linking descendants for [discardCaptured]'s transitive discard).
    private class Captured(
        val state: Any,
        val callSite: ObjectStableId?,
        val parentStableId: ObjectStableId?
    )

    // A node selected for capture at the [migrate] barrier: its identity snapshot (taken under lock) plus its
    // provider closure, which runs off-lock.
    private class CaptureSource(
        val stableId: ObjectStableId,
        val callSite: ObjectStableId?,
        val parentStableId: ObjectStableId?,
        val terminal: Boolean,
        val provider: () -> Any?
    )

    // Live-edit migration registers: the state captured from the torn-down definition keyed by stable id, and
    // the subset a node of the rebuilt definition has adopted via [Execution.restored] — the unclaimed
    // remainder are removed-element orphans, disposed by [sweepOrphans]. SETTLED (terminal) nodes are captured
    // alongside live ones (see [migrate] step 1 — a relaunching flavour needs the "done" state; the live frame
    // wins a stable-id collision), and adoption is call-site-gated ([restoredForNode]) — several invocations of
    // the same hosted document share a stable id, so invocation identity is what keeps one's state out of
    // another.
    private val migrationCaptured = HashMap<ObjectStableId, Captured>()
    private val claimedCaptures = HashSet<ObjectStableId>()

    // Resource registrations lifted off the torn-down tree at the [migrate] barrier, keyed by the owning
    // node's stable id, and re-adopted by the rebuilt node that shares it ([adoptLiftedResources]) — so an
    // open resource survives a live edit instead of being disposed by teardown (spec §5 "open resources").
    // Unlike [migrationCaptured] (claimed lazily by a user-code [Execution.restored] read, hence the separate
    // claimed-set), adoption here is eager and engine-driven at node spawn, so remove-on-adopt IS the claim.
    // Holds only what a frame OWNS — a call-site borrow is dropped at the barrier instead (see
    // [Binding.bootstrap]).
    private val migrationResources = HashMap<ObjectStableId, LiftedRegistrations>()

    // Managed bindings settled frames kept instead of disposing: a `manual` one at the root (§6's forgotten
    // close) and a `keepOnFailure` one on a frame that failed. Held here rather than left on the frame,
    // because a non-retained frame is compacted out of [nodes] when it settles and the entry would vanish
    // with it — retention that silently drops the registration while the process it names keeps running is a
    // leak, not inspection. Read via [retainedBindings], disposed via [releaseRetained].
    private val retained = ArrayList<Retained>()

    // A settled frame lifted off the torn-down tree at the [migrate] barrier: the frame itself plus every
    // runtime under it, so [buildNode] can still recurse once the frame is re-attached.
    private class RetiredFrame(
        val top: NodeRuntime,
        val subtree: List<NodeRuntime>
    )

    // Settled frames whose trace outlives the rebuild, keyed by the stable id of the node that HOSTED them,
    // and re-attached to the rebuilt node sharing that id ([adoptRetiredFrames]) — the third thing lifted
    // across the barrier, alongside [migrationCaptured] and [migrationResources]. Without it the rebuild
    // destroys the frame of anything that finished before the edit and is replay-adopted rather than re-run
    // (a completed RunStep's sub-Script), silently breaking the §5 promise that the trace is continuous
    // across the edit. Adoption is eager and engine-driven at node spawn, so remove-on-adopt IS the claim;
    // an unadopted frame (its host was removed) is dropped by [sweepOrphans].
    private val migrationRetiredFrames = HashMap<ObjectStableId, MutableList<RetiredFrame>>()

    // [nodeCounter] as of the last [migrate] barrier: a node whose ordinal is below it was minted by a
    // superseded generation, which is how [host] tells a carried frame from one this generation settled.
    private var migrationNodeWatermark = 0

    // A one-shot repositioning request carried across the [migrate] barrier and delivered to the single frame
    // its call-site path addresses (spec §4 "Repositioning"). Which frame that is lives per node in
    // [NodeRuntime.moveSuffix], handed one hop down at each [host]; this register holds the request itself and
    // gates BOTH [Execution] move surfaces, so clearing it silences them regardless of any suffix left on a
    // node. Read WITHOUT claiming. Every migrate overwrites it (an ordinary edit passes null, clearing it), so
    // it is one-shot by construction; also cleared by [sweepOrphans] so [close] leaves nothing behind.
    private var migrationMoveTarget: MoveTarget? = null

    // The stable identities the edit removed, reported by the driver at the [migrate] barrier and surfaced as
    // [Execution.removedStableIds]. An id is the element's address, and an address freed by a removal is
    // immediately reusable, so without this the engine would hand a removed element's capture and lifted
    // resources to whatever NEW element the edit created at the same address. Carried on the same one-shot
    // lifecycle as [migrationMoveTarget].
    private var migrationRemovedStableIds: Set<ObjectStableId> = emptySet()

    private var sequence = 0L
    private var nodeCounter = 0
    private var command: Command = Command.Paused
    private var breakpoints: Set<ObjectStableId> = emptySet()
    private var started = false
    private var cancelling = false
    private var migrating = false
    private var pauseOnError = false

    private val rootId = NodeId("n0")
    private var liveRootLogic: Logic = rootLogic

    // Set under [lock] whenever tree-visible state changes; [snapshot] then rebuilds the cached [published]
    // on the next read instead of on every mutation (the emit/log hot path).
    @Volatile
    private var dirty = false

    @Volatile
    private var published: RunState


    init {
        nodeCounter = 1
        nodes[rootId] = NodeRuntime(rootId, rootStableId, depth = 0, parentId = null, inputs = rootInputs)
        published = RunState(buildNode(rootId), sequence)
    }


    //----------------------------------------------------------------------------------- run-control surface (public)
    override fun snapshot(): RunState {
        if (!dirty) {
            return published
        }
        return synchronized(lock) {
            if (dirty) {
                published = RunState(buildNode(rootId), sequence)
                dirty = false
            }
            published
        }
    }


    override fun observe(listener: () -> Unit): AutoCloseable {
        synchronized(lock) {
            observers.add(listener)
        }
        return AutoCloseable {
            synchronized(lock) {
                observers.remove(listener)
            }
        }
    }


    /**
     * Subscribe to frame-close signals: [listener] is invoked exactly once per settled node, after its final
     * events are in [history], carrying the closed node (terminal status; children as of settle). Not invoked
     * for frames torn down by [migrate] — migration supersedes frames rather than closing them. Fires on an
     * engine dispatcher thread; keep listeners cheap. The returned handle unsubscribes.
     */
    fun observeFrames(listener: (Node) -> Unit): AutoCloseable {
        synchronized(lock) {
            frameObservers.add(listener)
        }
        return AutoCloseable {
            synchronized(lock) {
                frameObservers.remove(listener)
            }
        }
    }


    /**
     * Subscribe to live-trace reset signals ([Execution.resetEmitted]): [listener] is invoked synchronously
     * from the resetting spine, off the engine lock, BEFORE the reset call returns — so a consumer that
     * drains pending history and then clears is ordered exactly between the superseded pass's last emit and
     * the fresh pass's first. Fires on an engine dispatcher thread; keep listeners cheap. The returned
     * handle unsubscribes.
     */
    fun observeResets(listener: (TraceReset) -> Unit): AutoCloseable {
        synchronized(lock) {
            resetObservers.add(listener)
        }
        return AutoCloseable {
            synchronized(lock) {
                resetObservers.remove(listener)
            }
        }
    }


    override fun resume() {
        val toRelease = ArrayList<CompletableDeferred<Unit>>()
        synchronized(lock) {
            if (cancelling) {
                return
            }
            if (!started) {
                started = true
                command = Command.Running
                launchRoot()
            }
            else {
                command = Command.Running
                drainParked(toRelease)
            }
        }
        toRelease.forEach { it.complete(Unit) }
        publish()
    }


    override fun pause() {
        synchronized(lock) {
            if (cancelling) {
                return
            }
            // Overrides an in-flight stepping command too (a long step-over — e.g. over a sub-script — parks
            // at its next boundary instead of running the whole step to completion).
            command = Command.Paused
        }
        publish()
    }


    override fun step(mode: StepMode) {
        val toRelease = ArrayList<CompletableDeferred<Unit>>()
        synchronized(lock) {
            if (cancelling) {
                return
            }
            if (!started) {
                started = true
                command = Command.Paused
                launchRoot()
            }
            else {
                if (parked.isEmpty()) {
                    return
                }
                // The step is relative to the SHALLOWEST parked frame — the outermost pending wavefront — not the
                // deepest. For a single-spine Script/Flow exactly one node is parked, so min == max and this is a
                // no-op; but a concurrent Job parks siblings at different depths (workers at depth 1 while a
                // RunWorker's already-stepped-into child is parked at depth 2). Taking `maxOf` there would make
                // Step Over / Out reference the descended child's depth and re-descend into it; `minOf` keeps the
                // reference at the top-level worker wavefront so nested children stay below the limit (run free).
                val limit = parked.values.minOf { it.depth }
                command = when (mode) {
                    StepMode.Into -> Command.Paused
                    StepMode.Over -> Command.SteppingOver(limit)
                    StepMode.Out -> Command.SteppingOut(limit)
                }
                drainParked(toRelease)
            }
        }
        toRelease.forEach { it.complete(Unit) }
        publish()
    }


    override fun cancel() {
        val toRelease = ArrayList<CompletableDeferred<Unit>>()
        var settleRootCancelled = false
        var jobToCancel: Job? = null
        synchronized(lock) {
            if (cancelling) {
                return
            }
            cancelling = true
            if (!started) {
                started = true
                settleRootCancelled = true
            }
            else {
                drainParked(toRelease)
                // Cancel the run scope's Job too: a spine parked at a checkpoint is released by the drain (and
                // re-checks `cancelling`), but a spine suspended inside [Execution.blocking] awaits no latch —
                // only cancelling its coroutine reaches it, which runInterruptible converts to a thread
                // interrupt → CancellationException, settling it Cancelled like any other. Must run OUTSIDE the
                // lock: cancellation synchronously resumes cancelled continuations that re-enter [settleNode]'s
                // `synchronized(lock)` (same reason [toRelease] is completed off-lock).
                jobToCancel = scope.coroutineContext[Job]
            }
        }
        toRelease.forEach { it.complete(Unit) }
        jobToCancel?.cancel(CancellationException("Run cancelled"))
        if (settleRootCancelled) {
            settleNode(rootId, Outcome.Cancelled)
        }
        else {
            publish()
        }
    }


    override fun pauseOnError(enabled: Boolean) {
        synchronized(lock) {
            pauseOnError = enabled
        }
    }


    override fun setBreakpoints(ids: Set<ObjectStableId>) {
        synchronized(lock) {
            breakpoints = ids
        }
    }


    override fun request(node: NodeId, request: ExecutionRequest): ExecutionResult {
        val handler = synchronized(lock) {
            nodes[node]?.requestHandler
        }
        return handler?.invoke(request)
            ?: ExecutionFailure("No request handler for node: $node")
    }


    override fun history(sinceSequence: Long): List<TraceEvent> {
        return synchronized(lock) {
            // Sequence-ordered (single writer): binary-search the first event past the watermark.
            val search = history.binarySearchBy(sinceSequence + 1) { it.sequence }
            val start = if (search >= 0) search else -search - 1
            ArrayList(history.subList(start, history.size))
        }
    }


    override suspend fun await(): Outcome {
        return terminal.await()
    }


    /**
     * Stop the engine's thread pools while leaving the run's node tree and history fully readable — a
     * settled run's [snapshot] / [history] touch only [lock] + in-memory state, no dispatcher, so a
     * terminated run can be retained (post-run trace review) without holding threads. Called when the run
     * settles terminal. Migration-orphan disposal is deferred to [dispose] (bounded to one retention cycle,
     * matching the existing "orphan lingers at most one edit" invariant); node resources are already disposed
     * at [settleNode].
     */
    fun shutdown() {
        dispatcher.close()
        elasticDispatcher.close()
    }


    /**
     * Full teardown: dispose any migration orphans, then stop the pools. Called when a retained run is
     * replaced by a new one or the controller closes. Idempotent after [shutdown] ([dispatcher.close] is
     * [java.util.concurrent.ExecutorService.shutdownNow], which is idempotent).
     */
    fun dispose() {
        sweepOrphans()
        dispatcher.close()
        elasticDispatcher.close()
    }


    override fun close() {
        dispose()
    }


    /** Block the calling (non-dispatcher) thread until the run is quiescent — all spines parked or terminal. */
    fun awaitQuiescent() {
        dispatcher.awaitQuiescent()
    }


    // Test visibility: the number of live NodeRuntime entries — bounded on a streaming run iff frame
    // compaction works (see [settleNode]).
    internal fun nodeCount(): Int {
        return synchronized(lock) { nodes.size }
    }


    /**
     * Re-point a **quiescent** (paused / fully parked) run at [newRoot] — the live-edit migration barrier of
     * logic-spec §5. Captures every live node's durable state ([Execution.onCapture]) BEFORE teardown, cancels
     * and joins the old execution tree, then rebuilds a fresh tree against [newRoot] on a new coroutine scope —
     * carrying each captured state to the node of the new definition that shares its [stable id][ObjectStableId]
     * (surfaced there as [Execution.restored]). Open resource registrations are likewise lifted off each node
     * before teardown and re-adopted by the rebuilt node with the same stable id — an open resource survives
     * the edit (spec §5) rather than being disposed. A node the edit ADDED starts fresh (no matching capture);
     * a captured state no rebuilt node claims (a REMOVED element) is closed if [AutoCloseable] — and an
     * unclaimed lifted resource is disposed — by [sweepOrphans].
     * The run's history, sequence, observers and terminal handle are preserved — the trace is continuous across
     * the edit; only the LIVE execution tree is rebuilt. A frame that had already SETTLED with its trace
     * retained ([Execution.host]'s `retainTrace`) is likewise lifted and re-attached to the rebuilt node that
     * shares its host's stable id ([adoptRetiredFrames]), because nothing in the rebuild would re-create it:
     * a flavour that replay-adopts its completed elements never re-hosts them (see [Execution.restored]), so
     * the finished sub-execution would otherwise become unaddressable the moment the caller is edited.
     *
     * Must be called while the run is quiescent — every non-terminal node parked at a checkpoint and no
     * dispatch in flight (the caller awaits [awaitQuiescent] first), and never from a dispatcher thread.
     * [paused] starts the rebuilt run parked at its first wavefront (a step-after-edit); false resumes it.
     * [moveTarget] is an advisory one-shot repositioning request — a self-migration that repositions the run.
     * It is addressed to a single frame by [call-site path][MoveTarget.callSitePath] (empty = the root frame),
     * surfaced there as [Execution.moveTarget]; each frame on the way to it instead surfaces the hop it must
     * descend through as [Execution.moveDescendCallSite]. A flavour that doesn't support repositioning (or in
     * whose structure the id doesn't resolve) ignores it, leaving an ordinary migrate.
     * [removedStableIds] names the elements the edit REMOVED: their captures and lifted resources are held
     * back from adoption (and swept as orphans) and their breakpoints dropped, so an element the edit created
     * at a removed one's address starts clean instead of inheriting it. Also surfaced to the rebuilt tree as
     * [Execution.removedStableIds], for state a flavour keys the same way inside its own capture.
     */
    fun migrate(
        newRoot: Logic,
        paused: Boolean = true,
        moveTarget: MoveTarget? = null,
        removedStableIds: Set<ObjectStableId> = emptySet()
    ) {
        // Dispose orphans left unclaimed by a prior edit before this edit's captures overwrite the registers.
        sweepOrphans()

        // 1. Capture-before-teardown: snapshot each node's durable state while the run is still parked (so a
        // live handle can be detached before teardown would close it). Providers are user closures, run
        // off-lock; the run is quiescent, so a parked node is not mutating the state being read.
        // Settled (terminal) frames are captured too — a flavour that relaunches every element (a Job worker)
        // needs the completed element's "done" state on the rebuilt run so it doesn't redo its work. But
        // several invocations of one hosted document can share a stable id (a loop's retained settled
        // iterations plus the live in-flight one): ordering terminal sources first makes the LIVE frame's
        // capture win that key collision deterministically, instead of map order picking the winner.
        val providers = synchronized(lock) {
            check(!cancelling) { "Cannot migrate a cancelling run" }
            nodes.values.mapNotNull { runtime ->
                val provider = runtime.captureProvider
                    ?: return@mapNotNull null
                val parentStableId = runtime.parentId?.let { nodes.getValue(it).stableId }
                CaptureSource(
                    runtime.stableId, runtime.callerStableId, parentStableId,
                    runtime.status is NodeStatus.Terminal, provider)
            }
        }
        val captured = HashMap<ObjectStableId, Captured>()
        for (source in providers.sortedByDescending { it.terminal }) {
            source.provider()?.let {
                captured[source.stableId] = Captured(it, source.callSite, source.parentStableId)
            }
        }

        // 2. Teardown: cancel + join the old tree. Both registries are lifted off every node first (after the
        // capture providers ran, so they saw the intact world) — teardown's [settleFrame] then finds them
        // empty and open resources survive to be re-adopted by the rebuilt tree. Each stale coroutine unwinds
        // (running its finally / onClose for anything not lifted or detached); `migrating` suppresses its
        // settle so the run is neither published cancelled nor terminally completed. The join guarantees every
        // stale settle has run before the rebuild clears the node map below.
        val oldJob = synchronized(lock) {
            migrating = true
            for (runtime in nodes.values) {
                // Borrows are dropped rather than lifted — see [Binding.bootstrap].
                val owned = LinkedHashMap(runtime.bindings.filterValues { ! it.bootstrap })
                if (owned.isNotEmpty() || runtime.settleDisposals.isNotEmpty()) {
                    migrationResources[runtime.stableId] =
                        LiftedRegistrations(owned, ArrayList(runtime.settleDisposals))
                }
                runtime.bindings.clear()
                runtime.settleDisposals.clear()
            }
            scope.coroutineContext[Job]!!
        }
        runBlocking { oldJob.cancelAndJoin() }

        // 3. Rebuild: a fresh tree on a fresh scope (same dispatcher / thread pool), carrying the captured state
        // by stable id. Node ids keep advancing so a torn-down node id is never reused in the retained history.
        synchronized(lock) {
            // Lift the settled retained frames BEFORE the map is cleared — they are the run's finished
            // sub-executions, and the rebuild re-creates only what it re-runs.
            liftRetiredFrames()

            // Deliberately NOT cleared: `breakpoints` — stable-id keyed, it stays valid across the rebuild.
            nodes.clear()
            parked.clear()
            childLogic.clear()
            migrationCaptured.clear()
            migrationCaptured.putAll(captured)
            claimedCaptures.clear()
            migrationMoveTarget = moveTarget
            migrationRemovedStableIds = removedStableIds
            migrationNodeWatermark = nodeCounter

            // A breakpoint is stable-id keyed and otherwise survives the rebuild untouched, so a removed
            // element's breakpoint would land on whatever the edit created at its address.
            breakpoints = breakpoints - removedStableIds

            liveRootLogic = newRoot
            val rootRuntime = NodeRuntime(rootId, rootStableId, depth = 0, parentId = null, inputs = rootInputs)
            // The whole path starts here, so the root holds it in full: empty addresses the root itself, and
            // null (no request) leaves the root unaddressed rather than making it the target's frame.
            rootRuntime.moveSuffix = moveTarget?.callSitePath
            nodes[rootId] = rootRuntime
            adoptLiftedResources(rootRuntime)
            adoptRetiredFrames(rootRuntime)
            migrating = false
            cancelling = false
            started = true
            command = if (paused) Command.Paused else Command.Running
            scope = CoroutineScope(dispatcher + SupervisorJob())
            launchRoot()
        }
        publish()
    }


    // Closers and captured-state teardowns are third-party code, run off-lock: a failure must not break the
    // settle / supersede / sweep walk that claimed them, but it must not vanish either.
    private fun runCloserLogged(closer: () -> Unit) {
        try {
            closer()
        }
        catch (e: Throwable) {
            logger.warn("Resource closer failed", e)
        }
    }


    // Dispose any captured state no node of the rebuilt definition adopted (a removed element), and reset the
    // migration registers. Run at the next [migrate] and at [close]: within a run's life an orphaned detached
    // resource lingers at most one edit cycle (deliberate: no eager sweep on every edit). Unadopted lifted
    // resources are disposed regardless of [ClosePolicy] — the owner frame was removed by the edit, so no
    // explicit close (Manual) or failure inspection (KeepOnFailure) can ever reach them.
    private fun sweepOrphans() {
        val orphans = synchronized(lock) {
            val result = migrationCaptured
                .filterKeys { it !in claimedCaptures }
                .values
                .toList()
            migrationCaptured.clear()
            claimedCaptures.clear()
            migrationMoveTarget = null
            migrationRemovedStableIds = emptySet()
            // A settled frame no rebuilt node hosted — its host was removed by the edit — is simply dropped:
            // it holds no registration to close (resources were lifted separately at the barrier), and its
            // events stay in [history] like any other superseded frame's.
            migrationRetiredFrames.clear()
            result
        }
        orphans.forEach { captured ->
            (captured.state as? AutoCloseable)?.let { runCloserLogged { it.close() } }
        }

        val orphanedClosers = synchronized(lock) {
            val result = migrationResources.values.flatMap { lifted ->
                lifted.settleDisposals.asReversed().mapNotNull { it.claim() } +
                        lifted.bindings.values.toList().asReversed().mapNotNull { it.disposal?.claim() }
            }
            migrationResources.clear()
            result
        }
        orphanedClosers.forEach { runCloserLogged(it) }
    }


    // Must hold lock. Re-adopt any registrations lifted at the [migrate] barrier from the torn-down node that
    // shared this node's stable id; removal is the claim (see [migrationResources]).
    private fun adoptLiftedResources(runtime: NodeRuntime) {
        if (runtime.stableId in migrationRemovedStableIds) {
            return
        }
        migrationResources.remove(runtime.stableId)?.let {
            runtime.bindings.putAll(it.bindings)
            runtime.settleDisposals.addAll(it.settleDisposals)
        }
    }


    // Must hold lock, and must run BEFORE [nodes] is cleared. Lift every settled frame whose trace is
    // retained onto [migrationRetiredFrames], keyed by its HOST's stable id — the identity the rebuilt tree
    // will re-mint, exactly as for a lifted resource. Only the topmost such frame of a settled chain is
    // keyed: a settled retained parent carries its descendants in [RetiredFrame.subtree], and a settled
    // NON-retained parent already took its whole subtree out of [nodes] when it closed ([settleNode] ->
    // [removeSubtree]), so it cannot be seen here. The root is never lifted — it is re-created by the rebuild.
    private fun liftRetiredFrames() {
        fun retired(runtime: NodeRuntime): Boolean {
            return runtime.id != rootId &&
                    runtime.retainTrace &&
                    runtime.status is NodeStatus.Terminal
        }

        fun subtreeOf(runtime: NodeRuntime): List<NodeRuntime> {
            val result = mutableListOf(runtime)
            var index = 0
            while (index < result.size) {
                // Strict: a child id [nodes] no longer holds would leave the re-attached frame unbuildable
                // ([buildNode] resolves every child), and compaction removes the id from its parent's list in
                // the same breath as the node ([settleNode]) — so a miss here is a broken engine invariant.
                result[index].children.mapTo(result) { nodes.getValue(it) }
                index += 1
            }
            return result
        }

        for (runtime in nodes.values) {
            if (!retired(runtime)) {
                continue
            }
            val parentId = runtime.parentId
                ?: error("Non-root node without a parent: ${runtime.id}")
            val parent = nodes.getValue(parentId)
            if (retired(parent)) {
                // Carried by its own ancestor's [RetiredFrame.subtree].
                continue
            }
            migrationRetiredFrames
                .getOrPut(parent.stableId) { mutableListOf() }
                .add(RetiredFrame(runtime, subtreeOf(runtime)))
        }
    }


    // Must hold lock. Re-attach the settled frames lifted at the [migrate] barrier from the torn-down node
    // that shared this node's stable id; removal is the claim (see [migrationRetiredFrames]). A frame whose
    // CALL-SITE the edit removed is dropped rather than re-attached — deleting the element that hosted a
    // sub-execution takes that sub-execution's trace with it.
    private fun adoptRetiredFrames(parent: NodeRuntime) {
        if (parent.stableId in migrationRemovedStableIds) {
            return
        }
        val frames = migrationRetiredFrames.remove(parent.stableId)
            ?: return

        for (frame in frames) {
            if (frame.top.callerStableId in migrationRemovedStableIds) {
                continue
            }
            frame.subtree.forEach { nodes[it.id] = it }
            frame.top.parentId = parent.id
            parent.children.add(frame.top.id)
        }
    }


    // Must hold lock. A carried settled frame is superseded the moment its host re-invokes the same
    // definition from the same call-site: a flavour that relaunches every element on the rebuilt run (a Job
    // worker) would otherwise leave one stale duplicate per element per edit. Node ids are monotone and never
    // reused, so an ordinal below [migrationNodeWatermark] is precisely "minted by a superseded generation" —
    // no per-node flag needed. The superseded frame's events remain in [history].
    //
    // A re-invocation and a relaunch are indistinguishable at this seam, so a loop that re-hosts the same
    // sub-document after an edit also drops the carried frames of its PRE-edit iterations. That is the
    // deliberate trade: bounding memory (a relaunching flavour would otherwise keep one dead copy of every
    // element per edit, live values and all) beats retaining an iteration whose live values the loop's own
    // reset ([Execution.resetEmitted]) has already blanked.
    private fun supersedeRetiredFrames(parent: NodeRuntime, child: NodeRuntime) {
        if (migrationNodeWatermark == 0) {
            // No barrier has happened, so no child can be carried — skip the scan on the un-edited hot path.
            return
        }
        val superseded = parent.children.filter { childId ->
            val existing = nodes.getValue(childId)
            existing.id != child.id &&
                    nodeOrdinal(existing.id) < migrationNodeWatermark &&
                    existing.stableId == child.stableId &&
                    existing.callerStableId == child.callerStableId &&
                    existing.status is NodeStatus.Terminal
        }
        for (childId in superseded) {
            removeSubtree(childId)
            parent.children.remove(childId)
        }
    }


    // The ordinal of an engine-minted node id ("n0", "n1", ...); -1 for anything else (unreachable).
    private fun nodeOrdinal(nodeId: NodeId): Int {
        return nodeId.value.removePrefix("n").toIntOrNull() ?: -1
    }


    //--------------------------------------------------------------------------------------- engine internals (locked)
    private fun launchRoot() {
        // Must be called while holding lock; scope.launch dispatches synchronously (inFlight++ before return).
        scope.launch {
            runNode(rootId)
        }
    }


    private suspend fun runNode(nodeId: NodeId): Outcome {
        val execution = ExecutionImpl(nodeId)
        // Immutable per node; captured once so a failure catch can stamp [Outcome.Failed.at] with this node's id.
        val stableId = synchronized(lock) { nodes.getValue(nodeId).stableId }
        val outcome =
            try {
                Outcome.Success(rootOrChildLogic(nodeId).run(execution))
            }
            catch (_: CancellationException) {
                // Engine-driven cooperative cancel surfaced from a checkpoint.
                settleNode(nodeId, Outcome.Cancelled)
                return Outcome.Cancelled
            }
            catch (e: LogicFailure) {
                // A FRESH failure gets this node's id; a child failure re-thrown through [host] already carries
                // the originating child's id, which we preserve unchanged (spec §4 pause-reason propagation).
                Outcome.Failed(e.message ?: "failure", e.at ?: stableId)
            }
            catch (e: Throwable) {
                // The outcome keeps only the formatted message, so this is the one place the stack still exists
                // — without it a failed node is undiagnosable beyond its one-line message.
                logger.warn("Node failed: $nodeId", e)
                Outcome.Failed(ExceptionUtils.message(e), stableId)
            }
        settleNode(nodeId, outcome)
        return outcome
    }


    private fun rootOrChildLogic(nodeId: NodeId): Logic {
        return synchronized(lock) {
            if (nodeId == rootId) liveRootLogic else childLogic.getValue(nodeId)
        }
    }


    private suspend fun host(
        parentNodeId: NodeId,
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue,
        callerStableId: ObjectStableId?,
        retainTrace: Boolean,
        initialBindings: List<InitialBinding>,
        contextBarrier: Boolean
    ): TupleValue {
        val childId = synchronized(lock) {
            val parent = nodes.getValue(parentNodeId)
            val id = NodeId("n${nodeCounter++}")
            val runtime = NodeRuntime(
                id, stableId, parent.depth + 1, parentNodeId, inputs, callerStableId, retainTrace, contextBarrier)
            runtime.moveSuffix = inheritMoveSuffix(parent, callerStableId)
            if (runtime.moveSuffix != null && callerStableId != null) {
                // This hosting CLAIMED a descent hop, which means the transit frame ran to [callerStableId] with
                // its boundary suppressed — and a named boundary is the only writer of [NodeRuntime.position],
                // which starts null on every rebuild. Re-establish it here, or the frame reports no position at
                // all and its document loses the "element about to run" marker (and with it the move-to drag
                // handle that marker IS) until the run advances past the child. Scoped to the claimed hop, so a
                // host that never suppressed anything keeps whatever its own checkpoints recorded — notably a
                // Job worker, whose frame deliberately reports no position.
                parent.position = callerStableId
            }
            nodes[id] = runtime
            // BEFORE [adoptLiftedResources], and that placement IS the migration ordering rule — no precedence
            // logic needed, because adoption's putAll then overwrites any bootstrap value under the same key
            // (a borrow is re-supplied by the rebuilt caller; an adopted binding is the child's own — see
            // [Binding.bootstrap]). Direct map writes rather than [bind]: the bootstrap rests on the child
            // frame by construction, and routing through [exportOwnerOf] would consult an `exports` set that
            // is necessarily still empty here — the child re-declares its exports when its [Logic.run] starts,
            // which cannot have happened yet.
            for (initialBinding in initialBindings) {
                runtime.bindings[initialBinding.key] = Binding(initialBinding.value, null, bootstrap = true)
            }
            adoptLiftedResources(runtime)
            adoptRetiredFrames(runtime)
            childLogic[id] = child
            parent.children.add(id)
            supersedeRetiredFrames(parent, runtime)
            id
        }
        publish()

        val outcome = runNode(childId)

        return when (outcome) {
            is Outcome.Success -> outcome.value
            is Outcome.Failed -> throw LogicFailure(outcome.message, outcome.at)
            Outcome.Cancelled -> throw CancellationException("Child cancelled")
        }
    }


    // Must hold lock. Hand a [MoveTarget]'s remaining call-site path one hop down: the child inherits the
    // parent's suffix minus its own hop when the parent is a transit frame whose next hop IS this call-site,
    // and null (unaddressed) otherwise. A null [callerStableId] never matches — it is not a wildcard; a host
    // that names no distinct call-site simply cannot be path-addressed.
    //
    // Consumption is ONE-SHOT: the parent's suffix is cleared by the hosting that claims it, so a second
    // hosting from the same call-site in the same rebuild inherits nothing. Without that, a host that re-runs
    // its call-sites would re-apply the jump on a later pass — arbitrarily far from the request that asked
    // for it, with nothing on screen connecting the two.
    private fun inheritMoveSuffix(parent: NodeRuntime, callerStableId: ObjectStableId?): List<ObjectStableId>? {
        val suffix = parent.moveSuffix
        if (suffix.isNullOrEmpty() || callerStableId == null || suffix.first() != callerStableId) {
            return null
        }
        parent.moveSuffix = null
        return suffix.drop(1)
    }


    private suspend fun checkpoint(nodeId: NodeId, depth: Int, at: ObjectStableId?) {
        val deferred = synchronized(lock) {
            if (cancelling) {
                throw CancellationException("Run cancelled")
            }

            // Position updates whether or not this boundary parks; a null (anonymous) boundary preserves it.
            if (at != null) {
                nodes.getValue(nodeId).position = at
            }

            val reason: PauseReason? = if (at != null && at in breakpoints) {
                // Breakpoint: Explicit regardless of the in-flight command — a stepping command's would-be
                // Boundary settle here is upgraded so the client auto-step loop halts (spec §4) — and
                // stop-the-world: drop to Paused so concurrent spines park at their next boundary too
                // (mirroring pause()).
                command = Command.Paused
                PauseReason.Explicit
            }
            else when (val current = command) {
                Command.Running ->
                    null

                Command.Paused ->
                    PauseReason.Boundary

                // A stepping command STAYS active for the whole step (it does NOT collapse to Paused when a spine
                // parks at its boundary). Each qualifying spine still parks at its first boundary — one step — but
                // a spine BELOW the limit keeps running free for the entire step. Collapsing to Paused here would,
                // in a concurrent Job, let the first shallow worker to hit its boundary catch an already-running
                // deeper child at its next checkpoint and park it inside — re-descending under Step Over (the
                // reported bug). Leaving the command as SteppingOver/SteppingOut makes the deep child's run-free
                // race-free: its checkpoints are always > / >= the limit until it completes.
                is Command.SteppingOver ->
                    if (depth > current.limit) {
                        null
                    }
                    else {
                        PauseReason.Boundary
                    }

                is Command.SteppingOut ->
                    if (depth >= current.limit) {
                        null
                    }
                    else {
                        PauseReason.Boundary
                    }
            }

            if (reason == null) {
                null
            }
            else {
                park(nodeId, depth, reason)
            }
        }

        awaitRelease(nodeId, deferred)
    }


    private suspend fun pauseHere(nodeId: NodeId, reason: PauseReason) {
        val deferred = synchronized(lock) {
            if (cancelling) {
                throw CancellationException("Run cancelled")
            }
            park(nodeId, depthOf(nodeId), reason)
        }
        awaitRelease(nodeId, deferred)
    }


    private suspend fun <R> recoverable(nodeId: NodeId, onError: (Throwable) -> Unit, block: suspend () -> R): R {
        while (true) {
            try {
                return block()
            }
            catch (e: CancellationException) {
                // A cancel (engine-driven, surfaced from a checkpoint) is never recoverable.
                throw e
            }
            catch (e: Throwable) {
                // Render the failure (e.g. trace it on the failing element) before deciding park-vs-propagate.
                onError(e)
                val enabled = synchronized(lock) { pauseOnError }
                if (!enabled) {
                    throw e
                }
                // Pause-on-error: park this node Suspended(Error) WITHOUT unwinding — the caller's coroutine
                // stack (and its run-scoped state) stays alive, so a plain resume retries [block] here and an
                // edit-then-resume can capture this node's state at the migrate barrier. A cancel while
                // error-parked surfaces from pauseHere and propagates out (not re-caught — we are past block()).
                pauseHere(nodeId, PauseReason.Error)
            }
        }
    }


    // See [Execution.blocking]. Runs [block] on the elastic pool via runInterruptible so the fixed engine
    // thread is freed for the region's duration; the [CountingDispatcher] hold keeps the spine counted as busy
    // (so quiescence / migrate never read it as idle), and engine [cancel] — which cancels the run scope's Job
    // — interrupts the elastic worker thread, surfaced back as CancellationException so the node settles
    // Cancelled like any other.
    private suspend fun <R> blocking(block: () -> R): R {
        val hold = dispatcher.enterBlocking()
        try {
            return runInterruptible(elasticDispatcher) { block() }
        }
        finally {
            dispatcher.exitBlocking(hold)
        }
    }


    // Must hold lock.
    private fun park(nodeId: NodeId, depth: Int, reason: PauseReason): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        nodes.getValue(nodeId).status = NodeStatus.Suspended(reason)
        parked[nodeId] = Parked(deferred, depth)
        return deferred
    }


    private suspend fun awaitRelease(nodeId: NodeId, deferred: CompletableDeferred<Unit>?) {
        if (deferred == null) {
            return
        }
        publish()
        deferred.await()
        synchronized(lock) {
            if (cancelling) {
                throw CancellationException("Run cancelled")
            }
            nodes.getValue(nodeId).status = NodeStatus.Running
        }
        publish()
    }


    private fun settleNode(nodeId: NodeId, outcome: Outcome) {
        val proceed = synchronized(lock) {
            val runtime = nodes[nodeId]
                ?: return
            runtime.status = NodeStatus.Terminal(outcome)
            parked.remove(nodeId)
            // A node torn down by an in-progress [migrate] had its registrations lifted at the barrier (so the
            // settle below only sees late, unlifted ones), and is not published as terminal, frame-closed, nor
            // completes the run — the rebuilt tree supersedes it.
            !migrating
        }
        settleFrame(nodeId, error = outcome is Outcome.Failed)
        if (!proceed) {
            return
        }

        // Frame close: capture the settled node (its final events are already in [history]), compact, and
        // notify frame observers before the general change signal. The capture deep-copies the settled
        // subtree and nothing but the observers reads it, so with none registered there is nothing to build.
        val (closedNode, frameObserversCopy) = synchronized(lock) {
            val runtime = nodes.getValue(nodeId)
            val observersCopy = frameObservers.toList()
            val closedNode =
                if (observersCopy.isEmpty()) null
                else buildNode(nodeId)
            // The compiled Logic is never used after [host] returns; [migrate] clears the map wholesale.
            childLogic.remove(nodeId)
            if (!runtime.retainTrace && nodeId != rootId) {
                // Full compaction (§7 retention-vs-bounding): a settled non-retained frame — and its settled
                // subtree — leaves [nodes] and the parent's children, so it disappears from subsequent
                // snapshots and a streaming host stays O(live frames). Its events remain in [history].
                removeSubtree(nodeId)
                runtime.parentId?.let { nodes[it]?.children?.remove(nodeId) }
            }
            closedNode to observersCopy
        }
        if (closedNode != null) {
            frameObserversCopy.forEach { it(closedNode) }
        }
        publish()
        if (nodeId == rootId) {
            terminal.complete(outcome)
        }
    }


    // Must hold lock. Removes a compacted frame and any settled descendants still in the runtime maps
    // (retained descendants of a non-retained frame become unreachable from the tree, so they go too).
    private fun removeSubtree(nodeId: NodeId) {
        val runtime = nodes.remove(nodeId)
            ?: return
        childLogic.remove(nodeId)
        runtime.children.forEach { removeSubtree(it) }
    }


    /**
     * Settle one frame's two registries (logic-spec §6). Named bindings and anonymous disposals follow the
     * same rules with one exception: `manual` is a PROMOTION — it hands a binding one frame up so something
     * running later can still find and close it — and an anonymous registration has no name for anything to
     * find it by, which is why [SettleDisposalPolicy] has no such value.
     *
     * A binding with no disposal is a plain ambient value: its name goes out of scope and nothing is torn
     * down. Every disposal that does run is claimed under the lock (so exactly one caller wins across
     * supersession, explicit release and settle alike) and invoked OFF it — a closer is third-party code.
     */
    private fun settleFrame(nodeId: NodeId, error: Boolean) {
        val toDispose = synchronized(lock) {
            val runtime = nodes[nodeId] ?: return

            // A parent's own live binding under the same key wins over a hand-up (putIfAbsent): Auto's
            // disposal guarantee must not be displaced by an orphaned handle.
            val parent = runtime.parentId?.let { nodes[it] }
            val bindingClosers = ArrayList<() -> Unit>()
            val entries = runtime.bindings.entries.toList()
            runtime.bindings.clear()

            for ((key, binding) in entries) {
                val disposal = binding.disposal
                    ?: continue

                when (disposal.policy) {
                    ClosePolicy.Auto ->
                        disposal.claim()?.let { bindingClosers.add(it) }

                    ClosePolicy.Manual ->
                        // A barrier frame retains instead of handing up, exactly like the root — see
                        // [NodeRuntime.contextBarrier].
                        if (parent != null && ! runtime.contextBarrier) {
                            parent.bindings.putIfAbsent(key, binding)
                        }
                        else {
                            retained.add(Retained(nodeId, key, binding))
                        }

                    ClosePolicy.KeepOnFailure ->
                        if (error) {
                            retained.add(Retained(nodeId, key, binding))
                        }
                        else {
                            disposal.claim()?.let { bindingClosers.add(it) }
                        }
                }
            }

            val settleClosers = ArrayList<() -> Unit>()
            val anonymous = runtime.settleDisposals.toList()
            runtime.settleDisposals.clear()
            for (disposal in anonymous) {
                // KeepOnFailure on a failed frame leaves the side effect UNDONE, and there is no handle to
                // retain — the temp file simply stays for inspection — so the closer is never claimed.
                if (disposal.policy == ClosePolicy.KeepOnFailure && error) {
                    continue
                }
                disposal.claim()?.let { settleClosers.add(it) }
            }

            // LIFO within each registry, and anonymous cleanups ahead of binding disposals: an `onSettle`
            // typically tidies something produced USING a bound resource, so it has to run while that resource
            // is still open.
            settleClosers.asReversed() + bindingClosers.asReversed()
        }
        toDispose.forEach { runCloserLogged(it) }
    }


    private fun emit(nodeId: NodeId, address: Address, value: ExecutionValue, retain: Boolean) {
        synchronized(lock) {
            sequence += 1
            val runtime = nodes.getValue(nodeId)
            runtime.live[address] = value
            runtime.liveSequence[address] = sequence
            // Transient (retain = false): update the live view only, so a high-churn progress signal
            // doesn't grow history unboundedly (spec §7 retention-vs-bounding, per-emit).
            if (retain) {
                history.add(TraceEvent(sequence, nodeId, runtime.stableId, address, value))
            }
        }
        publish()
    }


    private fun log(nodeId: NodeId, value: ExecutionValue) {
        synchronized(lock) {
            sequence += 1
            val runtime = nodes.getValue(nodeId)
            history.add(TraceEvent(sequence, nodeId, runtime.stableId, null, value))
        }
        publish()
    }


    // See [Execution.resetEmitted]. The node's own live entries at [addresses] are removed; retained
    // (settled) child nodes hosted from [callSites] — transitively their entire subtrees — get their live
    // maps cleared for snapshot coherence with the trace consumer's cleared buffers (the nodes themselves
    // stay in the tree for history / call-site attribution). Listeners fire off-lock, synchronously, before
    // return (the [publish] copy-then-invoke pattern) — the ordering a drain-then-clear trace bridge needs.
    private fun resetEmitted(nodeId: NodeId, addresses: Collection<Address>, callSites: Collection<ObjectStableId>) {
        if (addresses.isEmpty() && callSites.isEmpty()) {
            return
        }
        val (reset, listeners) = synchronized(lock) {
            val runtime = nodes.getValue(nodeId)
            addresses.forEach { runtime.live.remove(it); runtime.liveSequence.remove(it) }
            // Nullable-element set so `in` accepts the nullable callerStableId (null is never a member).
            val sites = HashSet<ObjectStableId?>(callSites)
            for (childId in runtime.children) {
                val child = nodes[childId]
                    ?: continue
                if (child.callerStableId in sites) {
                    clearLiveSubtree(childId)
                }
            }
            dirty = true
            val reset = TraceReset(nodeId, runtime.stableId, addresses.toList(), callSites.toList())
            reset to resetObservers.toList()
        }
        listeners.forEach { it(reset) }
        publish()
    }


    // Must hold lock.
    private fun clearLiveSubtree(nodeId: NodeId) {
        val runtime = nodes[nodeId]
            ?: return
        runtime.live.clear()
        runtime.liveSequence.clear()
        runtime.children.forEach { clearLiveSubtree(it) }
    }


    private fun declareExport(nodeId: NodeId, selector: ExportSelector) {
        synchronized(lock) {
            val runtime = nodes.getValue(nodeId)

            // Refusal rather than silent no-op — see [NodeRuntime.contextBarrier].
            check(! runtime.contextBarrier) {
                "Context barrier frame cannot declare an export (hosted concurrently, so an upward binding " +
                        "would race its siblings): ${runtime.stableId.value} - $selector"
            }

            runtime.exports.add(selector)
        }
    }


    // Must hold lock. The furthest frame on [nodeId]'s self → parent → … → root chain reachable through an
    // UNBROKEN chain of export declarations: climb while the CURRENT frame declares an export COVERING [key].
    // The first frame that does not export is where the registration rests — so a provide nothing exports
    // stays on the opening frame, private by construction, and a frame of a flavour that never calls
    // [declareExport] ends every chain that reaches it. An actively running node's ancestors are always still
    // live, so the walk never dangles.
    //
    // A [NodeRuntime.contextBarrier] frame also ends the chain. The flag test is UNREACHABLE defence-in-depth:
    // [declareExport] refuses on a barrier frame and is the sole writer of [exports] (not lifted across
    // [migrate]), so a barrier frame's selector set is always empty and `none { covers }` already ends the
    // climb there. Kept so containment is a property of THIS walk rather than of a guard elsewhere staying
    // correct — a future writer of [exports] bypassing [declareExport] would silently reopen the
    // concurrent-sibling collision.
    private fun exportOwnerOf(nodeId: NodeId, key: ContextKey): NodeId {
        var current = nodeId
        while (true) {
            val runtime = nodes[current] ?: return current
            if (runtime.contextBarrier || runtime.exports.none { it.covers(key) }) {
                return current
            }
            current = runtime.parentId ?: return current
        }
    }


    // Must hold lock. Drop any BORROW under [key] resting on the frames the export climb travelled past —
    // [nodeId] up to but excluding [ownerId] — so that a frame's own bind is what its own reads resolve to
    // (rationale: [Binding.bootstrap]). Only borrows are touched: an owned binding on a frame in between is
    // somebody's live resource that dropping would strand unclosed.
    private fun supersedeBorrowsBelowOwner(nodeId: NodeId, ownerId: NodeId, key: ContextKey) {
        var current: NodeId? = nodeId
        while (current != null && current != ownerId) {
            val runtime = nodes[current] ?: break
            if (runtime.bindings[key]?.bootstrap == true) {
                runtime.bindings.remove(key)
            }
            current = runtime.parentId
        }
    }


    private fun bind(nodeId: NodeId, key: ContextKey, value: Any?, disposal: FrameDisposal?) {
        // A same-key re-bind SUPERSEDES: the displaced binding's disposal runs, because nothing else can ever
        // reach it once the map entry is gone — the settle walk and [releaseBinding] all resolve a key to
        // exactly one binding. Without this a loop that re-binds the same browser each iteration leaks every
        // iteration but the last. Claiming happens under the lock (so exactly one caller wins) while the
        // closer runs OFF it, AFTER the replacement is registered — the ordering a closer has to tolerate, and
        // why it must dispose the handle it captured rather than re-resolve by name.
        val displaced = synchronized(lock) {
            // A binding is disposed on its OWNING node's settle; ownership rests at the end of the export chain.
            val ownerId = exportOwnerOf(nodeId, key)
            supersedeBorrowsBelowOwner(nodeId, ownerId, key)
            nodes.getValue(ownerId).bindings.put(key, Binding(value, disposal))?.disposal?.claim()
        }
        displaced?.let { runCloserLogged(it) }
    }


    private fun onSettle(nodeId: NodeId, policy: SettleDisposalPolicy, closer: () -> Unit) {
        synchronized(lock) {
            nodes.getValue(nodeId).settleDisposals.add(FrameDisposal(policy.toClosePolicy(), closer))
        }
    }


    // Must hold lock. The nearest binding at [key] on the ancestor chain (self → parent → … → root), so a
    // nearer binding shadows a farther one and one resting on an ancestor frame it was exported to (or handed
    // up by a Manual settle) is reachable from any descendant of its owner.
    private fun bindingOf(nodeId: NodeId, key: ContextKey): Binding? {
        var current: NodeId? = nodeId
        while (current != null) {
            val runtime = nodes[current] ?: break
            runtime.bindings[key]?.let { return it }
            current = runtime.parentId
        }
        return null
    }


    // Must hold lock. The same walk as [bindingOf], removing the first match instead of reading it — so a
    // binding resting on an ancestor frame can be dropped by a descendant (a sibling closing step).
    //
    // Stops AFTER a [NodeRuntime.contextBarrier] frame's own registry rather than before it: the barrier frame
    // may still release what it holds itself, it just cannot reach past — see [NodeRuntime.contextBarrier].
    private fun removeNearestBinding(nodeId: NodeId, key: ContextKey): Binding? {
        var current: NodeId? = nodeId
        while (current != null) {
            val runtime = nodes[current] ?: break
            runtime.bindings.remove(key)?.let { return it }
            if (runtime.contextBarrier) {
                break
            }
            current = runtime.parentId
        }
        return null
    }


    // Must hold lock. Is any live binding on the ancestor chain keyed by something [predicate] accepts?
    private fun anyBindingOnChain(nodeId: NodeId, predicate: (ContextKey) -> Boolean): Boolean {
        var current: NodeId? = nodeId
        while (current != null) {
            val runtime = nodes[current] ?: break
            if (runtime.bindings.keys.any(predicate)) {
                return true
            }
            current = runtime.parentId
        }
        return false
    }


    private fun bindingFor(nodeId: NodeId, key: ContextKey): BindingLookup {
        synchronized(lock) {
            // Presence is registration-existence, never value-non-nullness: a binding that stored no handle is
            // Present(null), which is what keeps a nullable Context's deliberate null distinct from nothing
            // being bound at all.
            val binding = bindingOf(nodeId, key)
                ?: return BindingLookup.Missing
            return BindingLookup.Present(binding.value)
        }
    }


    private fun hasBinding(nodeId: NodeId, key: ContextKey): Boolean {
        synchronized(lock) {
            return anyBindingOnChain(nodeId) { it == key }
        }
    }


    private fun hasBindingInFamily(nodeId: NodeId, family: ContextFamily): Boolean {
        synchronized(lock) {
            return anyBindingOnChain(nodeId) { it.family == family }
        }
    }


    private fun releaseBinding(nodeId: NodeId, key: ContextKey) {
        val closer = synchronized(lock) {
            removeNearestBinding(nodeId, key)?.disposal?.claim()
        }
        closer?.let { runCloserLogged(it) }
    }


    private fun resourceValueFor(nodeId: NodeId, key: String): Any? {
        // An unparseable key addresses nothing: every key in a registry got there through ContextKey.parse.
        val contextKey = ContextKey.parseOrNull(key)
            ?: return null
        synchronized(lock) {
            return bindingOf(nodeId, contextKey)?.value
        }
    }


    private fun hasResourceInFamily(nodeId: NodeId, family: String): Boolean {
        synchronized(lock) {
            // Compared against the RENDERED key rather than the parsed family, deliberately: this entry point
            // accepts a fully-qualified string, which then matches only a binding under that whole string. See
            // the deprecation on [Execution.hasResourceInFamily] — the degradation is the reason it is
            // superseded, so it is preserved rather than quietly repaired.
            val qualifiedPrefix = "$family${ContextKey.qualifierDelimiter}"
            return anyBindingOnChain(nodeId) {
                val rendered = it.asString()
                rendered == family || rendered.startsWith(qualifiedPrefix)
            }
        }
    }


    private fun releaseResource(nodeId: NodeId, key: String) {
        // Removes WITHOUT claiming the disposal — this entry point exists for a caller that already tore the
        // resource down itself, so the auto-disposer must not fire afterwards. [releaseBinding] is the one
        // that disposes.
        val contextKey = ContextKey.parseOrNull(key)
            ?: return
        synchronized(lock) {
            removeNearestBinding(nodeId, contextKey)
        }
    }


    /**
     * The bindings settled frames RETAINED instead of disposing: a `manual` binding at the root (logic-spec
     * §6's forgotten close) and a `keepOnFailure` binding on a frame that failed, kept for inspection. This is
     * what makes "retain" mean something — the alternative is a registry entry that disappears while the
     * process it names keeps running.
     *
     * Spec-led, deliberately unconsumed (like [observeFrames]): awaiting a run-inspection consumer; exercised
     * by tests only.
     */
    fun retainedBindings(): List<RetainedBinding> {
        return synchronized(lock) {
            retained.map { RetainedBinding(it.nodeId, it.key, it.binding.value) }
        }
    }


    /**
     * Dispose the retained binding at [node] / [key] and drop it, at most once; false when nothing is retained
     * there. The explicit cleanup a `manual` binding was always waiting for, and the way an inspected
     * `keepOnFailure` resource is finally closed.
     *
     * Spec-led, deliberately unconsumed (like [retainedBindings]): awaiting a run-inspection consumer;
     * exercised by tests only.
     */
    fun releaseRetained(node: NodeId, key: ContextKey): Boolean {
        val closer = synchronized(lock) {
            val index = retained.indexOfFirst { it.nodeId == node && it.key == key }
            if (index == -1) {
                return false
            }
            retained.removeAt(index).binding.disposal?.claim()
        }
        closer?.let { runCloserLogged(it) }
        return true
    }


    private fun setRequestHandler(nodeId: NodeId, handler: (ExecutionRequest) -> ExecutionResult) {
        synchronized(lock) {
            nodes.getValue(nodeId).requestHandler = handler
        }
    }


    private fun setCaptureProvider(nodeId: NodeId, capture: () -> Any?) {
        synchronized(lock) {
            nodes.getValue(nodeId).captureProvider = capture
        }
    }


    // The state a predecessor node with this node's stable id captured across the live edit (null if none /
    // this node is new). Reading it claims the capture, so the orphan sweep won't dispose what was adopted.
    // Invocation identity: the capture is delivered only to a node hosted from the SAME call-site as the
    // captured invocation (null == null covers the root and hosts that name no distinct caller) — another
    // call-site re-hosting the same child document is a DIFFERENT invocation and starts fresh.
    // An id the edit REMOVED is likewise not adopted: this node is a different element the edit created at the
    // removed one's address. The capture stays unclaimed, so [sweepOrphans] disposes it.
    private fun restoredForNode(nodeId: NodeId): Any? {
        return synchronized(lock) {
            val runtime = nodes.getValue(nodeId)
            if (runtime.stableId in migrationRemovedStableIds) {
                return@synchronized null
            }
            val captured = migrationCaptured[runtime.stableId]
            if (captured == null || captured.callSite != runtime.callerStableId) {
                return@synchronized null
            }
            claimedCaptures.add(runtime.stableId)
            captured.state
        }
    }


    // See [Execution.discardCaptured]: remove the captures of invocations hosted from any of [callSites],
    // plus transitively their descendants' captures (linked by the barrier-time parent stable id) — an
    // abandoned invocation's nested hosts must not be adopted by the re-run's fresh invocations either.
    // A removed state never claimed via [restoredForNode] is closed like an orphan; a claimed one is only
    // dropped from the register (the claimant owns it).
    private fun discardCaptured(callSites: Collection<ObjectStableId>) {
        if (callSites.isEmpty()) {
            return
        }
        val unclaimedRemoved = synchronized(lock) {
            if (migrationCaptured.isEmpty()) {
                return
            }
            // Nullable-element sets so the `in` checks below accept the nullable callSite / parentStableId
            // (null is never a member — the root's null call-site can't be discarded).
            val sites = HashSet<ObjectStableId?>(callSites)
            val removedKeys = HashSet<ObjectStableId?>()
            val unclaimed = ArrayList<Any>()
            var progress = true
            while (progress) {
                progress = false
                val iterator = migrationCaptured.entries.iterator()
                while (iterator.hasNext()) {
                    val (stableId, captured) = iterator.next()
                    if (captured.callSite in sites || captured.parentStableId in removedKeys) {
                        iterator.remove()
                        removedKeys.add(stableId)
                        if (stableId !in claimedCaptures) {
                            unclaimed.add(captured.state)
                        }
                        claimedCaptures.remove(stableId)
                        progress = true
                    }
                }
            }
            unclaimed
        }
        unclaimedRemoved.forEach { state ->
            (state as? AutoCloseable)?.let { runCloserLogged { it.close() } }
        }
    }


    private fun depthOf(nodeId: NodeId): Int {
        return synchronized(lock) { nodes.getValue(nodeId).depth }
    }


    private fun nodeInputs(nodeId: NodeId): TupleValue {
        return synchronized(lock) { nodes.getValue(nodeId).inputs }
    }


    // Must hold lock.
    private fun drainParked(into: MutableList<CompletableDeferred<Unit>>) {
        parked.values.forEach { into.add(it.deferred) }
        parked.clear()
    }


    private fun publish() {
        val observersCopy = synchronized(lock) {
            dirty = true
            observers.toList()
        }
        observersCopy.forEach { it() }
    }


    // Must hold lock.
    private fun buildNode(nodeId: NodeId): Node {
        val runtime = nodes.getValue(nodeId)
        return Node(
            runtime.id,
            runtime.stableId,
            runtime.status,
            LinkedHashMap(runtime.live),
            runtime.children.map { buildNode(it) },
            runtime.callerStableId,
            runtime.retainTrace,
            runtime.position,
            LinkedHashMap(runtime.liveSequence)
        )
    }


    //----------------------------------------------------------------------------------------------- execution context
    private inner class ExecutionImpl(
        private val nodeId: NodeId
    ): Execution {
        // Immutable per node — captured once so the checkpoint / inputs hot paths take no lock.
        private val depth = depthOf(nodeId)

        override val inputs: TupleValue = nodeInputs(nodeId)

        override suspend fun checkpoint(at: ObjectStableId?) =
            this@RunEngine.checkpoint(nodeId, depth, at)

        override fun emit(address: Address, value: ExecutionValue, retain: Boolean) =
            this@RunEngine.emit(nodeId, address, value, retain)

        override fun log(value: ExecutionValue) =
            this@RunEngine.log(nodeId, value)

        override fun resetEmitted(addresses: Collection<Address>, callSites: Collection<ObjectStableId>) =
            this@RunEngine.resetEmitted(nodeId, addresses, callSites)

        override suspend fun pauseHere(reason: PauseReason) =
            this@RunEngine.pauseHere(nodeId, reason)

        override suspend fun <R> recoverable(onError: (Throwable) -> Unit, block: suspend () -> R): R =
            this@RunEngine.recoverable(nodeId, onError, block)

        override suspend fun <R> blocking(block: () -> R): R =
            this@RunEngine.blocking(block)

        override suspend fun host(
            stableId: ObjectStableId,
            child: Logic,
            inputs: TupleValue,
            callerStableId: ObjectStableId?,
            retainTrace: Boolean,
            initialBindings: List<InitialBinding>,
            contextBarrier: Boolean
        ): TupleValue =
            this@RunEngine.host(
                nodeId, stableId, child, inputs, callerStableId, retainTrace, initialBindings, contextBarrier)

        override fun declareExport(selector: ExportSelector) =
            this@RunEngine.declareExport(nodeId, selector)

        @Deprecated(
            "Declare an ExportSelector — a bare family and a qualified member are different claims",
            ReplaceWith("declareExport(ExportSelector.parse(key))"))
        override fun declareExport(key: String) =
            this@RunEngine.declareExport(nodeId, ExportSelector.parse(key))

        override fun binding(key: ContextKey): BindingLookup =
            this@RunEngine.bindingFor(nodeId, key)

        override fun hasBinding(key: ContextKey): Boolean =
            this@RunEngine.hasBinding(nodeId, key)

        override fun hasBindingInFamily(family: ContextFamily): Boolean =
            this@RunEngine.hasBindingInFamily(nodeId, family)

        override fun bind(key: ContextKey, value: Any?, disposal: FrameDisposal?) =
            this@RunEngine.bind(nodeId, key, value, disposal)

        override fun releaseBinding(key: ContextKey) =
            this@RunEngine.releaseBinding(nodeId, key)

        override fun onSettle(policy: SettleDisposalPolicy, closer: () -> Unit) =
            this@RunEngine.onSettle(nodeId, policy, closer)

        // The raw string interop layer routes into exactly the same engine operations as the typed API — one
        // registry, one lookup — which is what makes a raw caller and a typed one address one registration.
        override fun resource(key: String, policy: ClosePolicy, value: Any?, closer: () -> Unit) =
            this@RunEngine.bind(nodeId, ContextKey.parse(key), value, FrameDisposal(policy, closer))

        override fun resourceValue(key: String): Any? =
            this@RunEngine.resourceValueFor(nodeId, key)

        @Deprecated(
            "A fully-qualified key silently degrades this to an exact-key check",
            ReplaceWith("hasBindingInFamily(ContextFamily(family))"))
        override fun hasResourceInFamily(family: String): Boolean =
            this@RunEngine.hasResourceInFamily(nodeId, family)

        override fun releaseResource(key: String) =
            this@RunEngine.releaseResource(nodeId, key)

        override fun onRequest(handler: (ExecutionRequest) -> ExecutionResult) =
            this@RunEngine.setRequestHandler(nodeId, handler)

        override fun onCapture(capture: () -> Any?) =
            this@RunEngine.setCaptureProvider(nodeId, capture)

        override val restored: Any?
            get() = this@RunEngine.restoredForNode(nodeId)

        override val moveTarget: ObjectStableId?
            get() = synchronized(lock) {
                val request = migrationMoveTarget
                    ?: return@synchronized null
                val suffix = nodes.getValue(nodeId).moveSuffix
                    ?: return@synchronized null
                // An exhausted path means this node IS the addressed frame; hops still remaining mean it is a
                // transit frame, which reads [moveDescendCallSite] instead.
                if (suffix.isEmpty()) request.target else null
            }

        override val moveDescendCallSite: ObjectStableId?
            get() = synchronized(lock) {
                if (migrationMoveTarget == null) {
                    return@synchronized null
                }
                nodes.getValue(nodeId).moveSuffix?.firstOrNull()
            }

        override val removedStableIds: Set<ObjectStableId>
            get() = synchronized(lock) { migrationRemovedStableIds }

        override fun discardCaptured(callSites: Collection<ObjectStableId>) =
            this@RunEngine.discardCaptured(callSites)
    }
}
