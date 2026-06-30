package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val rootLogic: Logic,
    rootStableId: ObjectStableId,
    rootInputs: TupleValue = TupleValue.empty,
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
        val inputs: TupleValue
    ) {
        var status: NodeStatus = NodeStatus.Running
        val live = LinkedHashMap<Address, ExecutionValue>()
        val children = ArrayList<NodeId>()
        val resources = LinkedHashMap<String, Registration>()
        var requestHandler: ((ExecutionRequest) -> ExecutionResult)? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val lock = Any()
    private val dispatcher = CountingDispatcher(threads)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val nodes = HashMap<NodeId, NodeRuntime>()
    private val parked = HashMap<NodeId, Parked>()
    private val history = ArrayList<TraceEvent>()
    private val observers = ArrayList<(RunState) -> Unit>()
    private val terminal = CompletableDeferred<Outcome>()

    private var sequence = 0L
    private var nodeCounter = 0
    private var command: Command = Command.Paused
    private var started = false
    private var cancelling = false
    private var pauseOnError = false

    private val rootId = NodeId("n0")

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
                val limit = parked.values.maxOf { it.depth }
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
        dispatcher.close()
    }


    /** Block the calling (non-dispatcher) thread until the run is quiescent — all spines parked or terminal. */
    fun awaitQuiescent() {
        dispatcher.awaitQuiescent()
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


    // Each node's Logic: the root uses rootLogic; children carry their own Logic in a side map.
    private fun rootOrChildLogic(nodeId: NodeId): Logic {
        if (nodeId == rootId) {
            return rootLogic
        }
        return synchronized(lock) { childLogic.getValue(nodeId) }
    }

    private val childLogic = HashMap<NodeId, Logic>()


    private suspend fun host(
        parentNodeId: NodeId,
        stableId: ObjectStableId,
        child: Logic,
        inputs: TupleValue
    ): TupleValue {
        val childId = synchronized(lock) {
            val parent = nodes.getValue(parentNodeId)
            val id = NodeId("n${nodeCounter++}")
            nodes[id] = NodeRuntime(id, stableId, parent.depth + 1, inputs)
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

                is Command.SteppingOver ->
                    if (depth > current.limit) {
                        null
                    }
                    else {
                        command = Command.Paused
                        PauseReason.Boundary
                    }

                is Command.SteppingOut ->
                    if (depth >= current.limit) {
                        null
                    }
                    else {
                        command = Command.Paused
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
        synchronized(lock) {
            nodes.getValue(nodeId).status = NodeStatus.Terminal(outcome)
            parked.remove(nodeId)
        }
        disposeResources(nodeId, error = outcome is Outcome.Failed)
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
            runtime.children.map { buildNode(it) }
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

        override suspend fun host(stableId: ObjectStableId, child: Logic, inputs: TupleValue): TupleValue =
            this@RunEngine.host(nodeId, stableId, child, inputs)

        override fun resource(key: String, policy: ClosePolicy, closer: () -> Unit) =
            this@RunEngine.registerResource(nodeId, key, policy, closer)

        override fun releaseResource(key: String) =
            this@RunEngine.releaseResource(nodeId, key)

        override fun onRequest(handler: (ExecutionRequest) -> ExecutionResult) =
            this@RunEngine.setRequestHandler(nodeId, handler)
    }
}
