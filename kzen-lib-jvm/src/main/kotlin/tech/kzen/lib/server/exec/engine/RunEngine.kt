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
 * fold [sequence] and rebuilds the immutable [RunState] snapshot published via the [published] volatile —
 * so concurrent readers (UI, tests) see a coherent whole-tree value with no locking, and parallel worker
 * coroutines (run on the [CountingDispatcher]) never touch shared state directly; they only emit through the
 * [Execution] handed to them, which routes back into this single writer.
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
        val closer: () -> Unit
    )


    private class NodeRuntime(
        val id: NodeId,
        val stableId: ObjectStableId,
        val depth: Int,
        val inputs: TupleValue,
        // The element that hosted this node (a RunStep / Job worker), carried to [Node.callerStableId] for
        // trace attribution; null for the root and for a host that named no distinct caller.
        val callerStableId: ObjectStableId? = null,
        // Whether this node's trace buffer is retained after the frame closes, carried to [Node.retainTrace]
        // (§7 retention-vs-bounding); false lets a trace consumer evict a streaming host's per-element frame on
        // settle. Always true for the root.
        val retainTrace: Boolean = true
    ) {
        var status: NodeStatus = NodeStatus.Running
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
    private val observers = ArrayList<(RunState) -> Unit>()
    private val terminal = CompletableDeferred<Outcome>()

    // Live-edit migration registers: the state captured from the torn-down definition keyed by stable id, and
    // the subset a node of the rebuilt definition has adopted via [Execution.restored] — the unclaimed
    // remainder are removed-element orphans, disposed by [sweepOrphans].
    private val migrationCaptured = HashMap<ObjectStableId, Any>()
    private val claimedCaptures = HashSet<ObjectStableId>()

    private var sequence = 0L
    private var nodeCounter = 0
    private var command: Command = Command.Paused
    private var started = false
    private var cancelling = false
    private var migrating = false
    private var pauseOnError = false

    private val rootId = NodeId("n0")
    private var liveRootLogic: Logic = rootLogic

    @Volatile
    private var published: RunState


    init {
        nodeCounter = 1
        nodes[rootId] = NodeRuntime(rootId, rootStableId, depth = 0, inputs = rootInputs)
        published = RunState(buildNode(rootId), sequence)
    }


    //----------------------------------------------------------------------------------- run-control surface (public)
    override fun snapshot(): RunState {
        return published
    }


    override fun observe(listener: (RunState) -> Unit): AutoCloseable {
        synchronized(lock) {
            observers.add(listener)
        }
        return AutoCloseable {
            synchronized(lock) {
                observers.remove(listener)
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
            if (command == Command.Running) {
                command = Command.Paused
            }
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


    override fun request(node: NodeId, request: ExecutionRequest): ExecutionResult {
        val handler = synchronized(lock) {
            nodes[node]?.requestHandler
        }
        return handler?.invoke(request)
            ?: ExecutionFailure("No request handler for node: $node")
    }


    override fun history(sinceSequence: Long): List<TraceEvent> {
        return synchronized(lock) {
            history.filter { it.sequence > sinceSequence }
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


    /**
     * Re-point a **quiescent** (paused / fully parked) run at [newRoot] — the live-edit migration barrier of
     * logic-spec §5. Captures every live node's durable state ([Execution.onCapture]) BEFORE teardown, cancels
     * and joins the old execution tree, then rebuilds a fresh tree against [newRoot] on a new coroutine scope —
     * carrying each captured state to the node of the new definition that shares its [stable id][ObjectStableId]
     * (surfaced there as [Execution.restored]). A node the edit ADDED starts fresh (no matching capture); a
     * captured state no rebuilt node claims (a REMOVED element) is closed if [AutoCloseable] by [sweepOrphans].
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

        // 2. Teardown: cancel + join the old tree. Each stale coroutine unwinds (running its finally / onClose
        // for any resource the capture did NOT detach); `migrating` suppresses its settle so the run is neither
        // published cancelled nor terminally completed. The join guarantees every stale settle has run before
        // the rebuild clears the node map below.
        val oldJob = synchronized(lock) {
            migrating = true
            scope.coroutineContext[Job]!!
        }
        runBlocking { oldJob.cancelAndJoin() }

        // 3. Rebuild: a fresh tree on a fresh scope (same dispatcher / thread pool), carrying the captured state
        // by stable id. Node ids keep advancing so a torn-down node id is never reused in the retained history.
        synchronized(lock) {
            nodes.clear()
            parked.clear()
            childLogic.clear()
            migrationCaptured.clear()
            migrationCaptured.putAll(captured)
            claimedCaptures.clear()

            liveRootLogic = newRoot
            nodes[rootId] = NodeRuntime(rootId, rootStableId, depth = 0, inputs = rootInputs)
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
    // resource lingers at most one edit cycle (a deliberate simplification of the old eager per-flavour sweep).
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
            catch (e: CancellationException) {
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
            nodes[id] = NodeRuntime(id, stableId, parent.depth + 1, inputs, callerStableId, retainTrace)
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


    private suspend fun checkpoint(nodeId: NodeId, depth: Int) {
        val deferred = synchronized(lock) {
            if (cancelling) {
                throw CancellationException("Run cancelled")
            }
            val reason: PauseReason? = when (val current = command) {
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
            // A node torn down by an in-progress [migrate] still disposes its (non-detached) resources, but is
            // not published as terminal nor completes the run — the rebuilt tree supersedes it.
            !migrating
        }
        disposeResources(nodeId, error = outcome is Outcome.Failed)
        if (!proceed) {
            return
        }
        publish()
        if (nodeId == rootId) {
            terminal.complete(outcome)
        }
    }


    private fun disposeResources(nodeId: NodeId, error: Boolean) {
        val toDispose = synchronized(lock) {
            val runtime = nodes[nodeId] ?: return
            val ordered = runtime.resources.values.toList().asReversed()
            runtime.resources.clear()
            ordered.filter { registration ->
                when (registration.policy) {
                    ClosePolicy.Auto -> true
                    ClosePolicy.Manual -> false
                    ClosePolicy.KeepOnFailure -> !error
                }
            }
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


    private fun registerResource(nodeId: NodeId, key: String, policy: ClosePolicy, closer: () -> Unit) {
        synchronized(lock) {
            nodes.getValue(nodeId).resources[key] = Registration(policy, closer)
        }
    }


    private fun releaseResource(nodeId: NodeId, key: String) {
        synchronized(lock) {
            nodes.getValue(nodeId).resources.remove(key)
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
        val (snapshot, observersCopy) = synchronized(lock) {
            val snapshot = RunState(buildNode(rootId), sequence)
            published = snapshot
            snapshot to observers.toList()
        }
        observersCopy.forEach { it(snapshot) }
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
            runtime.retainTrace
        )
    }


    //----------------------------------------------------------------------------------------------- execution context
    private inner class ExecutionImpl(
        private val nodeId: NodeId
    ): Execution {
        override val inputs: TupleValue
            get() = nodeInputs(nodeId)

        override suspend fun checkpoint() =
            this@RunEngine.checkpoint(nodeId, depthOf(nodeId))

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

        override fun resource(key: String, policy: ClosePolicy, closer: () -> Unit) =
            this@RunEngine.registerResource(nodeId, key, policy, closer)

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
