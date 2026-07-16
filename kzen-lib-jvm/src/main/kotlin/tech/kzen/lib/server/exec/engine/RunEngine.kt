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
import java.util.concurrent.Executors
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeId
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.ResourceScope
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
    private sealed interface Command {
        data object Running: Command
        data object Paused: Command
        data class SteppingOver(val limit: Int): Command
        data class SteppingOut(val limit: Int): Command
    }


    private class Parked(
        val deferred: CompletableDeferred<Unit>,
        val depth: Int,
        @Suppress("unused") val reason: PauseReason
    )


    private class Registration(
        val policy: ClosePolicy,
        val value: Any?,
        val closer: () -> Unit
    )


    private class NodeRuntime(
        val id: NodeId,
        val stableId: ObjectStableId,
        val depth: Int,
        // The node that hosted this one (one level up); null for the root. Used to resolve
        // [ResourceScope.Parent] ownership at resource registration.
        val parentId: NodeId?,
        val inputs: TupleValue,
        // The element that hosted this node (a RunStep / Job worker), carried to [Node.callerStableId] for
        // trace attribution; null for the root and for a host that named no distinct caller.
        val callerStableId: ObjectStableId? = null,
        // Whether this node's trace buffer is retained after the frame closes, carried to [Node.retainTrace]
        // (§7 retention-vs-bounding); false makes the engine compact the frame out of [nodes] / the snapshot
        // tree on settle (see [settleNode]), and the frame-close signal ([observeFrames]) lets a trace
        // consumer evict its buffer likewise. Always true for the root.
        val retainTrace: Boolean = true
    ) {
        var status: NodeStatus = NodeStatus.Running
        // Last named boundary reached, carried to [Node.position]; anonymous checkpoints leave it unchanged.
        // Starts null on a migrate rebuild too — re-established when the rebuilt spine re-parks at its boundary.
        var position: ObjectStableId? = null
        val live = LinkedHashMap<Address, ExecutionValue>()
        // Parallel to [live], carried to [Node.liveSequence]: the write sequence of each live entry.
        val liveSequence = LinkedHashMap<Address, Long>()
        val children = ArrayList<NodeId>()
        val resources = LinkedHashMap<String, Registration>()
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
    // remainder are removed-element orphans, disposed by [sweepOrphans]. Only LIVE (non-terminal) nodes are
    // captured, and adoption is call-site-gated ([restoredForNode]) — several invocations of the same hosted
    // document share a stable id, so invocation identity is what keeps one's state out of another.
    private val migrationCaptured = HashMap<ObjectStableId, Captured>()
    private val claimedCaptures = HashSet<ObjectStableId>()

    // Resource registrations lifted off the torn-down tree at the [migrate] barrier, keyed by the owning
    // node's stable id, and re-adopted by the rebuilt node that shares it ([adoptLiftedResources]) — so an
    // open resource survives a live edit instead of being disposed by teardown (spec §5 "open resources").
    // Unlike [migrationCaptured] (claimed lazily by a user-code [Execution.restored] read, hence the separate
    // claimed-set), adoption here is eager and engine-driven at node spawn, so remove-on-adopt IS the claim.
    private val migrationResources = HashMap<ObjectStableId, LinkedHashMap<String, Registration>>()

    // A one-shot repositioning target carried across the [migrate] barrier, surfaced to every node of the
    // rebuilt tree as [Execution.moveTarget] (spec §4 "Repositioning"). Tree-wide (not keyed by node) and read
    // WITHOUT claiming — root and hosted children may all read it during one barrier's rebuild. Every migrate
    // overwrites it (an ordinary edit passes null, clearing it), so it is one-shot by construction; also
    // cleared by [sweepOrphans] so [close] leaves nothing behind.
    private var migrationMoveTarget: ObjectStableId? = null

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
     * the edit; only the execution tree is rebuilt.
     *
     * Must be called while the run is quiescent — every non-terminal node parked at a checkpoint and no
     * dispatch in flight (the caller awaits [awaitQuiescent] first), and never from a dispatcher thread.
     * [paused] starts the rebuilt run parked at its first wavefront (a step-after-edit); false resumes it.
     * [moveTarget] is an advisory one-shot repositioning hint surfaced to the rebuilt tree as
     * [Execution.moveTarget] — a self-migration that repositions the run; a flavour that doesn't support
     * repositioning (or in whose structure the id doesn't resolve) ignores it, leaving an ordinary migrate.
     */
    fun migrate(newRoot: Logic, paused: Boolean = true, moveTarget: ObjectStableId? = null) {
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

        // 2. Teardown: cancel + join the old tree. Resource registrations are lifted off every node first
        // (after the capture providers ran, so they saw the intact world) — teardown's [disposeResources]
        // then finds empty maps and open resources survive to be re-adopted by the rebuilt tree. Each stale
        // coroutine unwinds (running its finally / onClose for anything not lifted or detached); `migrating`
        // suppresses its settle so the run is neither published cancelled nor terminally completed. The join
        // guarantees every stale settle has run before the rebuild clears the node map below.
        val oldJob = synchronized(lock) {
            migrating = true
            for (runtime in nodes.values) {
                if (runtime.resources.isNotEmpty()) {
                    migrationResources[runtime.stableId] = LinkedHashMap(runtime.resources)
                    runtime.resources.clear()
                }
            }
            scope.coroutineContext[Job]!!
        }
        runBlocking { oldJob.cancelAndJoin() }

        // 3. Rebuild: a fresh tree on a fresh scope (same dispatcher / thread pool), carrying the captured state
        // by stable id. Node ids keep advancing so a torn-down node id is never reused in the retained history.
        synchronized(lock) {
            // Deliberately NOT cleared: `breakpoints` — stable-id keyed, it stays valid across the rebuild.
            nodes.clear()
            parked.clear()
            childLogic.clear()
            migrationCaptured.clear()
            migrationCaptured.putAll(captured)
            claimedCaptures.clear()
            migrationMoveTarget = moveTarget

            liveRootLogic = newRoot
            val rootRuntime = NodeRuntime(rootId, rootStableId, depth = 0, parentId = null, inputs = rootInputs)
            nodes[rootId] = rootRuntime
            adoptLiftedResources(rootRuntime)
            migrating = false
            cancelling = false
            started = true
            command = if (paused) Command.Paused else Command.Running
            scope = CoroutineScope(dispatcher + SupervisorJob())
            launchRoot()
        }
        publish()
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
            result
        }
        orphans.forEach { captured ->
            (captured.state as? AutoCloseable)?.let { runCatching { it.close() } }
        }

        val orphanedResources = synchronized(lock) {
            val result = migrationResources.values.map { it.values.toList().asReversed() }
            migrationResources.clear()
            result
        }
        orphanedResources.forEach { registrations ->
            registrations.forEach { runCatching { it.closer() } }
        }
    }


    // Must hold lock. Re-adopt any resource registrations lifted at the [migrate] barrier from the torn-down
    // node that shared this node's stable id; removal is the claim (see [migrationResources]).
    private fun adoptLiftedResources(runtime: NodeRuntime) {
        migrationResources.remove(runtime.stableId)?.let {
            runtime.resources.putAll(it)
        }
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
                Outcome.Failed(ExceptionUtils.message(e), stableId)
            }
        settleNode(nodeId, outcome)
        return outcome
    }


    // Each node's Logic: the root uses the (possibly migrated) live root logic; children carry their own Logic
    // in a side map.
    private fun rootOrChildLogic(nodeId: NodeId): Logic {
        return synchronized(lock) {
            if (nodeId == rootId) liveRootLogic else childLogic.getValue(nodeId)
        }
    }

    private val childLogic = HashMap<NodeId, Logic>()


    private suspend fun host(
        parentNodeId: NodeId,
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue,
        callerStableId: ObjectStableId?,
        retainTrace: Boolean
    ): TupleValue {
        val childId = synchronized(lock) {
            val parent = nodes.getValue(parentNodeId)
            val id = NodeId("n${nodeCounter++}")
            val runtime = NodeRuntime(id, stableId, parent.depth + 1, parentNodeId, inputs, callerStableId, retainTrace)
            nodes[id] = runtime
            adoptLiftedResources(runtime)
            childLogic[id] = child
            parent.children.add(id)
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
        parked[nodeId] = Parked(deferred, depth, reason)
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
            // A node torn down by an in-progress [migrate] had its resources lifted at the barrier (so the
            // dispose below only sees late, unlifted registrations), and is not published as terminal,
            // frame-closed, nor completes the run — the rebuilt tree supersedes it.
            !migrating
        }
        disposeResources(nodeId, error = outcome is Outcome.Failed)
        if (!proceed) {
            return
        }

        // Frame close: capture the settled node (its final events are already in [history]), compact, and
        // notify frame observers before the general change signal.
        val (closedNode, frameObserversCopy) = synchronized(lock) {
            val runtime = nodes.getValue(nodeId)
            val closedNode = buildNode(nodeId)
            // The compiled Logic is never used after [host] returns; [migrate] clears the map wholesale.
            childLogic.remove(nodeId)
            if (!runtime.retainTrace && nodeId != rootId) {
                // Full compaction (§7 retention-vs-bounding): a settled non-retained frame — and its settled
                // subtree — leaves [nodes] and the parent's children, so it disappears from subsequent
                // snapshots and a streaming host stays O(live frames). Its events remain in [history].
                removeSubtree(nodeId)
                runtime.parentId?.let { nodes[it]?.children?.remove(nodeId) }
            }
            closedNode to frameObservers.toList()
        }
        frameObserversCopy.forEach { it(closedNode) }
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


    private fun disposeResources(nodeId: NodeId, error: Boolean) {
        val toDispose = synchronized(lock) {
            val runtime = nodes[nodeId] ?: return
            val entries = runtime.resources.entries.toList()
            runtime.resources.clear()

            // A Manual registration survives its owner's settle (§6: only an explicit closing action disposes
            // it) — hand it up to the parent so it stays on the ancestor chain, readable ([resourceValueFor])
            // and releasable ([releaseResource]) by whatever runs after the owner. At the root there is no
            // parent: it leaves the registry and stays alive past the run (the §6 "forgotten close"), as does
            // a KeepOnFailure registration retained on its failed owner for inspection. A parent's own live
            // registration under the same key wins over a hand-up (putIfAbsent) — Auto's disposal guarantee
            // must not be displaced by an orphaned handle.
            val parent = runtime.parentId?.let { nodes[it] }
            val dispose = ArrayList<Registration>()
            for ((key, registration) in entries) {
                when (registration.policy) {
                    ClosePolicy.Auto ->
                        dispose.add(registration)
                    ClosePolicy.Manual ->
                        parent?.resources?.putIfAbsent(key, registration)
                    ClosePolicy.KeepOnFailure ->
                        if (!error) {
                            dispose.add(registration)
                        }
                }
            }
            dispose.asReversed()
        }
        toDispose.forEach { registration ->
            runCatching { registration.closer() }
        }
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


    private fun registerResource(
        nodeId: NodeId, key: String, policy: ClosePolicy, scope: ResourceScope, value: Any?, closer: () -> Unit
    ) {
        synchronized(lock) {
            // Resolve the owning node from [scope]: the resource is disposed on that node's settle. An actively
            // running node's ancestors are always still live, so the resolved target exists.
            val ownerId = when (scope) {
                ResourceScope.Self -> nodeId
                ResourceScope.Parent -> nodes.getValue(nodeId).parentId ?: nodeId
                ResourceScope.Root -> rootId
            }
            nodes.getValue(ownerId).resources[key] = Registration(policy, value, closer)
        }
    }


    private fun resourceValueFor(nodeId: NodeId, key: String): Any? {
        synchronized(lock) {
            // Same ancestor-chain walk as [releaseResource]: a resource registered on this node or handed up
            // the tree via [ResourceScope] is readable from any descendant of its owner.
            var current: NodeId? = nodeId
            while (current != null) {
                val runtime = nodes[current] ?: break
                runtime.resources[key]?.let { return it.value }
                current = runtime.parentId
            }
            return null
        }
    }


    private fun releaseResource(nodeId: NodeId, key: String) {
        synchronized(lock) {
            // Walk the ancestor chain (self → parent → … → root) and remove the first match, so a resource handed
            // up the tree via [ResourceScope] can be deregistered by a descendant (e.g. a sibling closing step).
            var current: NodeId? = nodeId
            while (current != null) {
                val runtime = nodes[current] ?: break
                if (runtime.resources.remove(key) != null) {
                    return
                }
                current = runtime.parentId
            }
        }
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
    private fun restoredForNode(nodeId: NodeId): Any? {
        return synchronized(lock) {
            val runtime = nodes.getValue(nodeId)
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
            (state as? AutoCloseable)?.let { runCatching { it.close() } }
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
            retainTrace: Boolean
        ): TupleValue =
            this@RunEngine.host(nodeId, stableId, child, inputs, callerStableId, retainTrace)

        override fun resource(key: String, policy: ClosePolicy, scope: ResourceScope, value: Any?, closer: () -> Unit) =
            this@RunEngine.registerResource(nodeId, key, policy, scope, value, closer)

        override fun resourceValue(key: String): Any? =
            this@RunEngine.resourceValueFor(nodeId, key)

        override fun releaseResource(key: String) =
            this@RunEngine.releaseResource(nodeId, key)

        override fun onRequest(handler: (ExecutionRequest) -> ExecutionResult) =
            this@RunEngine.setRequestHandler(nodeId, handler)

        override fun onCapture(capture: () -> Any?) =
            this@RunEngine.setCaptureProvider(nodeId, capture)

        override val restored: Any?
            get() = this@RunEngine.restoredForNode(nodeId)

        override val moveTarget: ObjectStableId?
            get() = synchronized(lock) { migrationMoveTarget }

        override fun discardCaptured(callSites: Collection<ObjectStableId>) =
            this@RunEngine.discardCaptured(callSites)
    }
}
