package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val children = ArrayList<NodeId>()
        val resources = LinkedHashMap<String, Registration>()
        var requestHandler: ((ExecutionRequest) -> ExecutionResult)? = null
        var captureProvider: (() -> Any?)? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val lock = Any()
    private val dispatcher = CountingDispatcher(threads)
    private var scope = CoroutineScope(dispatcher + SupervisorJob())

    private val nodes = HashMap<NodeId, NodeRuntime>()
    private val parked = HashMap<NodeId, Parked>()
    private val history = ArrayList<TraceEvent>()
    private val observers = ArrayList<() -> Unit>()
    private val frameObservers = ArrayList<(Node) -> Unit>()
    private val terminal = CompletableDeferred<Outcome>()

    // Live-edit migration registers: the state captured from the torn-down definition keyed by stable id, and
    // the subset a node of the rebuilt definition has adopted via [Execution.restored] — the unclaimed
    // remainder are removed-element orphans, disposed by [sweepOrphans].
    private val migrationCaptured = HashMap<ObjectStableId, Any>()
    private val claimedCaptures = HashSet<ObjectStableId>()

    // Resource registrations lifted off the torn-down tree at the [migrate] barrier, keyed by the owning
    // node's stable id, and re-adopted by the rebuilt node that shares it ([adoptLiftedResources]) — so an
    // open resource survives a live edit instead of being disposed by teardown (spec §5 "open resources").
    // Unlike [migrationCaptured] (claimed lazily by a user-code [Execution.restored] read, hence the separate
    // claimed-set), adoption here is eager and engine-driven at node spawn, so remove-on-adopt IS the claim.
    private val migrationResources = HashMap<ObjectStableId, LinkedHashMap<String, Registration>>()

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
            }
        }
        toRelease.forEach { it.complete(Unit) }
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


    override fun close() {
        sweepOrphans()
        dispatcher.close()
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
     */
    fun migrate(newRoot: Logic, paused: Boolean = true) {
        // Dispose orphans left unclaimed by a prior edit before this edit's captures overwrite the registers.
        sweepOrphans()

        // 1. Capture-before-teardown: snapshot each live node's durable state while it is still parked (so a
        // live handle can be detached before teardown would close it). Providers are user closures, run
        // off-lock; the run is quiescent, so a parked node is not mutating the state being read.
        val providers = synchronized(lock) {
            check(!cancelling) { "Cannot migrate a cancelling run" }
            nodes.values.mapNotNull { runtime ->
                runtime.captureProvider?.let { runtime.stableId to it }
            }
        }
        val captured = HashMap<ObjectStableId, Any>()
        for ((stableId, provider) in providers) {
            provider()?.let { captured[stableId] = it }
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
            result
        }
        orphans.forEach { state ->
            (state as? AutoCloseable)?.let { runCatching { it.close() } }
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
                Outcome.Failed(e.message ?: "failure")
            }
            catch (e: Throwable) {
                Outcome.Failed(ExceptionUtils.message(e))
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
            is Outcome.Failed -> throw LogicFailure(outcome.message)
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


    private fun emit(nodeId: NodeId, address: Address, value: ExecutionValue) {
        synchronized(lock) {
            sequence += 1
            val runtime = nodes.getValue(nodeId)
            runtime.live[address] = value
            history.add(TraceEvent(sequence, nodeId, runtime.stableId, address, value))
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
    private fun restoredForNode(nodeId: NodeId): Any? {
        return synchronized(lock) {
            val stableId = nodes.getValue(nodeId).stableId
            val state = migrationCaptured[stableId]
            if (state != null) {
                claimedCaptures.add(stableId)
            }
            state
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
            runtime.position
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

        override fun emit(address: Address, value: ExecutionValue) =
            this@RunEngine.emit(nodeId, address, value)

        override fun log(value: ExecutionValue) =
            this@RunEngine.log(nodeId, value)

        override suspend fun pauseHere(reason: PauseReason) =
            this@RunEngine.pauseHere(nodeId, reason)

        override suspend fun <R> recoverable(onError: (Throwable) -> Unit, block: suspend () -> R): R =
            this@RunEngine.recoverable(nodeId, onError, block)

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
    }
}
