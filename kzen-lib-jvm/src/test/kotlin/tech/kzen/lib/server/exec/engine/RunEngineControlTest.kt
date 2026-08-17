package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.StepMode
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Run-control semantics of [RunEngine]: the run / step / pause / resume lifecycle, step-over across
 * concurrent spines, breakpoints, cancellation and shutdown, [Execution.blocking], failure attribution
 * ([Outcome.Failed.at]), and the emit hot path with frame compaction.
 */
class RunEngineControlTest {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * A depth-1 "worker" that parks once at its own checkpoint, then hosts a multi-step child at depth 2 (a
     * RunWorker-shaped nested Logic), then keeps parking — so after the child completes it stays in the tree at
     * depth 1, the way a Job Worker awaits more input. Mirrors a `RunWorker` hosting its instructions Script.
     */
    private class RunHostLogic(private val childSteps: Int): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            execution.checkpoint()
            execution.host(ObjectStableId("child"), StepsLogic(childSteps))
            while (true) {
                execution.checkpoint()
            }
        }
    }


    /**
     * A Job-shaped concurrent tree: a "background" Worker looping at depth 1 alongside a "run" Worker that hosts
     * a multi-step child at depth 2. Its purpose is to reproduce the concurrent-spine wavefront a single-spine
     * Script never produces — the exact shape (a depth-1 sibling parked while a depth-2 hosted child is parked)
     * that Step Over must run free instead of descending into.
     */
    private class JobShapedLogic(private val backgroundSteps: Int, private val childSteps: Int): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            coroutineScope {
                async { execution.host(ObjectStableId("background"), StepsLogic(backgroundSteps)) }
                async { execution.host(ObjectStableId("run"), RunHostLogic(childSteps)) }
            }
            return TupleValue.ofMain("done")
        }
    }


    /**
     * Runs a single [Execution.recoverable] unit that throws on its first [failBefore] attempts, then succeeds —
     * the shape of a recoverable failure that a fix (or a transient condition clearing) eventually lets proceed.
     * Records every attempt + every rendered error so a test can assert retry behaviour under pause-on-error.
     */
    private class FlakyLogic(
        private val failBefore: Int,
        private val attempts: java.util.concurrent.atomic.AtomicInteger,
        private val renderedErrors: MutableList<String>
    ): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            return execution.recoverable({ t -> renderedErrors.add(t.message ?: "?") }) {
                val attempt = attempts.incrementAndGet()
                if (attempt <= failBefore) {
                    throw RuntimeException("boom $attempt")
                }
                TupleValue.ofMain(attempt.toLong())
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runToSuccessEmitsAndLogs() = runBlocking {
        val engine = RunEngine(StepsLogic(3), rootId)
        try {
            engine.resume()
            val outcome = engine.await()

            val success = assertIs<Outcome.Success>(outcome)
            assertEquals(3, success.value.mainComponentValue())

            val snapshot = engine.snapshot()
            assertEquals(ExecutionValue.of(3L), snapshot.root.live[Address.of("i")])
            assertTrue(snapshot.root.status is NodeStatus.Terminal)

            // History retains every iteration: 3 emits + 3 logs = 6 events, deterministically sequenced.
            val history = engine.history(0)
            assertEquals(6, history.size)
            assertEquals((1L .. 6L).toList(), history.map { it.sequence })
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun stepAdvancesOneBoundaryAtATime() = runBlocking {
        val engine = RunEngine(StepsLogic(3), rootId)
        try {
            // Start: run to the first checkpoint (before any emit) and pause there.
            engine.step()
            engine.awaitQuiescent()
            var snapshot = engine.snapshot()
            assertTrue(snapshot.root.status is NodeStatus.Suspended)
            assertEquals(0, snapshot.root.live.size)

            // One step: emit i=1, settle before the second checkpoint.
            engine.step()
            engine.awaitQuiescent()
            snapshot = engine.snapshot()
            assertTrue(snapshot.root.status is NodeStatus.Suspended)
            assertEquals(ExecutionValue.of(1L), snapshot.root.live[Address.of("i")])

            // Run to completion from here.
            engine.resume()
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)
            assertEquals(ExecutionValue.of(3L), engine.snapshot().root.live[Address.of("i")])
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun positionTracksNamedBoundariesAcrossParkAndRelease() = runBlocking {
        // Named boundaries before each element, plus an anonymous mid-element checkpoint that must NOT
        // blank the recorded position (a step's internal pausability checkpoint).
        val logic = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue {
                for (i in 1 .. 2) {
                    execution.checkpoint(ObjectStableId("step-$i"))
                    execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
                    execution.checkpoint()
                }
                return TupleValue.ofMain(2)
            }
        }

        val engine = RunEngine(logic, rootId)
        try {
            // Park at the first named boundary: position recorded even though the element hasn't run yet.
            engine.step()
            engine.awaitQuiescent()
            var snapshot = engine.snapshot()
            assertTrue(snapshot.root.status is NodeStatus.Suspended)
            assertEquals(ObjectStableId("step-1"), snapshot.root.position)

            // Park at the anonymous checkpoint after element 1: position preserved, not cleared.
            engine.step()
            engine.awaitQuiescent()
            snapshot = engine.snapshot()
            assertEquals(ExecutionValue.of(1L), snapshot.root.live[Address.of("i")])
            assertEquals(ObjectStableId("step-1"), snapshot.root.position)

            // Park at the second named boundary: position advances.
            engine.step()
            engine.awaitQuiescent()
            snapshot = engine.snapshot()
            assertEquals(ObjectStableId("step-2"), snapshot.root.position)

            // Run to completion: the last named position survives in the terminal snapshot.
            engine.resume()
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)
            assertEquals(ObjectStableId("step-2"), engine.snapshot().root.position)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun nestedHostRunsChildAsConfinedNode() = runBlocking {
        val hosting = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue {
                execution.checkpoint()
                val childOutput = execution.host(ObjectStableId("child"), StepsLogic(2))
                val childValue = (childOutput.mainComponentValue() as Int).toLong()
                execution.emit(Address.of("childResult"), ExecutionValue.of(childValue))
                return TupleValue.ofMain("done")
            }
        }

        val engine = RunEngine(hosting, rootId)
        try {
            engine.resume()
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)

            val snapshot = engine.snapshot()
            assertEquals(1, snapshot.root.children.size)
            val child = snapshot.root.children.single()
            assertEquals(ObjectStableId("child"), child.stableId)
            assertEquals(ExecutionValue.of(2L), child.live[Address.of("i")])
            assertEquals(ExecutionValue.of(2L), snapshot.root.live[Address.of("childResult")])
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun cancelSettlesCancelledAndDisposesResources() = runBlocking {
        var disposed = false
        val logic = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue {
                execution.resource("r", ClosePolicy.Auto) { disposed = true }
                while (true) {
                    execution.checkpoint()
                    execution.emit(Address.of("tick"), ExecutionValue.of(true))
                }
            }
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertTrue(engine.snapshot().root.status is NodeStatus.Suspended)

            engine.cancel()
            engine.awaitQuiescent()

            val outcome = engine.await()
            assertEquals(Outcome.Cancelled, outcome)
            assertEquals(NodeStatus.Terminal(Outcome.Cancelled), engine.snapshot().root.status)
            assertTrue(disposed)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun parallelChildrenRunConcurrentlyAndQuiesce() = runBlocking {
        val parent = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue {
                coroutineScope {
                    val a = async { execution.host(ObjectStableId("a"), StepsLogic(3)) }
                    val b = async { execution.host(ObjectStableId("b"), StepsLogic(3)) }
                    a.await()
                    b.await()
                }
                return TupleValue.ofMain("done")
            }
        }

        val engine = RunEngine(parent, rootId, threads = 4)
        try {
            engine.resume()
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)

            val snapshot = engine.snapshot()
            assertEquals(2, snapshot.root.children.size)
            snapshot.root.children.forEach { child ->
                assertEquals(ExecutionValue.of(3L), child.live[Address.of("i")])
                assertTrue(child.status is NodeStatus.Terminal)
            }
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun recoverableFailurePropagatesWhenPauseOnErrorDisabled() = runBlocking {
        // Default (pause-on-error off): a recoverable unit's failure settles the node failed, rendered once.
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val errors = mutableListOf<String>()
        val engine = RunEngine(FlakyLogic(failBefore = 1, attempts, errors), rootId)
        try {
            engine.resume()
            val outcome = engine.await()

            assertTrue(assertIs<Outcome.Failed>(outcome).message.contains("boom 1"))
            assertEquals(1, attempts.get())
            assertEquals(listOf("boom 1"), errors)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun recoverableFailureParksErrorThenRetriesToSuccessOnResume() = runBlocking {
        // Pause-on-error on: the unit fails its first two attempts, parking Suspended(Error) each time instead
        // of failing the run; each resume retries; the third attempt succeeds and the run completes. Proves the
        // engine parks-without-unwinding and re-runs the recoverable unit on resume (fix-and-resume mechanics).
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val errors = mutableListOf<String>()
        val engine = RunEngine(FlakyLogic(failBefore = 2, attempts, errors), rootId)
        try {
            engine.pauseOnError(true)

            engine.resume()
            engine.awaitQuiescent()
            assertEquals(NodeStatus.Suspended(PauseReason.Error), engine.snapshot().root.status)
            assertEquals(1, attempts.get())

            engine.resume()
            engine.awaitQuiescent()
            assertEquals(NodeStatus.Suspended(PauseReason.Error), engine.snapshot().root.status)
            assertEquals(2, attempts.get())

            engine.resume()
            val outcome = engine.await()
            assertEquals(3L, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
            assertEquals(listOf("boom 1", "boom 2"), errors)
        }
        finally {
            engine.close()
        }
    }


    //---------------------------------------------------------- settled engine stays readable after shutdown
    @Test
    fun shutdownKeepsSnapshotAndHistoryReadableAfterTerminal() = runBlocking {
        // A terminated engine can be retained for post-run review: shutdown() stops the pools, but snapshot()
        // and history() stay readable (lock + in-memory only, no dispatcher). dispose() then fully tears down.
        val engine = RunEngine(StepsLogic(2), rootId)
        engine.resume()
        engine.await()

        engine.shutdown()

        val root = engine.snapshot().root
        assertIs<NodeStatus.Terminal>(root.status, "run settled terminal")
        assertEquals(ExecutionValue.of(2L), root.live[Address.of("i")], "live view readable after shutdown")
        assertEquals(2, engine.history(0).count { it.address != null }, "history readable after shutdown")

        engine.dispose()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun stepOverRunsAlreadyDescendedConcurrentChildFree() = runBlocking {
        // The Job step-over bug: a concurrent tree where a depth-1 "run" Worker has been stepped INTO, so its
        // hosted child is parked at depth 2 while a sibling "background" Worker is parked at depth 1. A Step Over
        // here must run the depth-2 child FREE (it is nested below the outermost pending wavefront), NOT descend /
        // stay in it. The single-spine Script tests never exercise this because a Script parks exactly one node at
        // a time (min == max depth); only a concurrent Job parks siblings at different depths.
        val engine = RunEngine(JobShapedLogic(backgroundSteps = 5, childSteps = 3), rootId, threads = 4)
        try {
            // Into: settle at the entry wavefront (background + run parked at depth 1, no child yet).
            engine.step()
            engine.awaitQuiescent()

            // Into again: run passes its checkpoint and hosts the child, which parks at depth 2 (we have now
            // descended into the child) while background re-parks at depth 1.
            engine.step()
            engine.awaitQuiescent()
            assertEquals(2, deepestSuspendedDepth(engine.snapshot().root), "precondition: descended into child")

            // Step Over: the child (depth 2, below the shallowest parked frame at depth 1) must run free to
            // completion, leaving no parked node deeper than the depth-1 worker wavefront.
            engine.step(tech.kzen.lib.common.exec.engine.StepMode.Over)
            engine.awaitQuiescent()

            val root = engine.snapshot().root
            assertEquals(
                1, deepestSuspendedDepth(root),
                "Step Over must not leave a spine parked inside the nested child")

            val child = root.children.single { it.stableId == ObjectStableId("run") }
                .children.single { it.stableId == ObjectStableId("child") }
            assertIs<NodeStatus.Terminal>(child.status, "the nested child ran free to completion under Step Over")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Depth of the deepest node currently Suspended (parked at a checkpoint), or -1 if none is; the root is depth 0.
    private fun deepestSuspendedDepth(node: tech.kzen.lib.common.exec.engine.Node, depth: Int = 0): Int {
        val here = if (node.status is NodeStatus.Suspended) depth else -1
        val below = node.children.maxOfOrNull { deepestSuspendedDepth(it, depth + 1) } ?: -1
        return maxOf(here, below)
    }


    //------------------------------------------------------------------- engine hot path + frame compaction (phase 1)
    /** Loops [total] boundaries; signals [reached] after the [signalAt]-th emit, then blocks on [gate]. */
    private class GatedLoopLogic(
        private val total: Int,
        private val signalAt: Int,
        private val reached: CountDownLatch,
        private val gate: CountDownLatch
    ): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            for (i in 1 .. total) {
                execution.checkpoint()
                execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
                if (i == signalAt) {
                    reached.countDown()
                    gate.await()
                }
            }
            return TupleValue.ofMain(total)
        }
    }


    @Test
    fun pauseOverridesInFlightStepOver() = runBlocking {
        // A long Step Over (over a hosted child running many boundaries) must be pausable: Paused overrides the
        // in-flight stepping command, so the run settles Suspended(Boundary) at the child's next checkpoint
        // long before the step would have completed. (Previously pause only acted on a Running command — a
        // step-over could only be cancelled.)
        val reached = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val hosting = logicOf { execution ->
            execution.checkpoint()
            execution.host(ObjectStableId("child"), GatedLoopLogic(10_000, 5, reached, gate))
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(hosting, rootId, threads = 4)
        try {
            // Park at the root's first checkpoint (depth 0).
            engine.step()
            engine.awaitQuiescent()

            // Step Over: the hosted child (depth 1 > limit 0) runs free...
            engine.step(StepMode.Over)
            reached.await()
            // ...until pause overrides the stepping command mid-step.
            engine.pause()
            gate.countDown()
            engine.awaitQuiescent()

            val child = engine.snapshot().root.children.single()
            assertEquals(
                NodeStatus.Suspended(PauseReason.Boundary), child.status,
                "pause mid-step must park the child at its next boundary")
            assertEquals(
                ExecutionValue.of(5L), child.live[Address.of("i")],
                "the child parked long before the step would have completed")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun frameCompactionBoundsStreamingHost() = runBlocking {
        // §7 retention-vs-bounding: a streaming host (one retainTrace = false child per element) stays
        // O(live frames) — each settled non-retained frame is compacted out of the runtime maps and the
        // snapshot tree, its frame-close signal fires exactly once with its final status, and its events
        // remain in history untouched.
        val count = 1000
        val streaming = logicOf { execution ->
            for (i in 1 .. count) {
                execution.host(ObjectStableId("el-$i"), StepsLogic(1), retainTrace = false)
            }
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(streaming, rootId)
        try {
            val closed = ConcurrentLinkedQueue<Node>()
            engine.observeFrames { closed.add(it) }

            engine.resume()
            assertIs<Outcome.Success>(engine.await())

            assertTrue(
                engine.snapshot().root.children.isEmpty(),
                "settled non-retained frames are compacted out of the snapshot")
            assertEquals(1, engine.nodeCount(), "only the (retained) root remains in the runtime maps")

            val childCloses = closed.filter { it.stableId != rootId }
            assertEquals(count, childCloses.size, "frame-close fires exactly once per frame")
            assertEquals(count, childCloses.map { it.id }.toSet().size)
            assertTrue(childCloses.all { it.status is NodeStatus.Terminal && !it.retainTrace })
            assertIs<NodeStatus.Terminal>(
                closed.single { it.stableId == rootId }.status,
                "the root's own frame-close carries its terminal status")

            assertEquals(2 * count, engine.history(0).size, "compaction leaves history untouched")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun perfGuardHotPathIsNotQuadratic() = runBlocking {
        // Coarse guard for the emit/publish hot path: 100k history events on a ~10-node tree, with a
        // bridge-shaped observer pulling history by watermark on every change signal. The bound is generous —
        // it only catches an O(N²) regression (rebuilding the snapshot tree per emit, or scanning the full
        // history list per pull), which overshoots it by an order of magnitude.
        val children = 10
        val stepsPerChild = 5_000
        val hosting = logicOf { execution ->
            for (i in 1 .. children) {
                execution.host(ObjectStableId("c$i"), StepsLogic(stepsPerChild))
            }
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(hosting, rootId)
        try {
            val watermark = AtomicLong(0)
            engine.observe {
                engine.history(watermark.get()).lastOrNull()?.let { watermark.set(it.sequence) }
            }

            val elapsed = measureTimeMillis {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals((2L * children * stepsPerChild), engine.snapshot().sequence)
            assertTrue(elapsed < 10_000, "hot path regressed: ${2 * children * stepsPerChild} events took ${elapsed}ms")
        }
        finally {
            engine.close()
        }
    }


    //--------------------------------------------------------------------------------------- breakpoints (phase 3)
    @Test
    fun breakpointParksFullSpeedRunExplicitAtPosition() = runBlocking {
        val engine = RunEngine(namedStepsLogic(3), rootId)
        try {
            engine.setBreakpoints(setOf(ObjectStableId("step-2")))
            engine.resume()
            engine.awaitQuiescent()

            val snapshot = engine.snapshot()
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), snapshot.root.status)
            assertEquals(ObjectStableId("step-2"), snapshot.root.position)
            assertEquals(
                ExecutionValue.of(1L), snapshot.root.live[Address.of("i")],
                "parked before element 2 ran")

            // The check happens on arrival: resuming proceeds past the parked boundary without re-triggering.
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(ExecutionValue.of(3L), engine.snapshot().root.live[Address.of("i")])
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun setBreakpointsReplaceSetClears() = runBlocking {
        val engine = RunEngine(namedStepsLogic(4), rootId)
        try {
            engine.setBreakpoints(setOf(ObjectStableId("step-1")))
            engine.resume()
            engine.awaitQuiescent()
            assertEquals(ObjectStableId("step-1"), engine.snapshot().root.position)

            // Replace-set: the old breakpoint is gone, the new one parks.
            engine.setBreakpoints(setOf(ObjectStableId("step-3")))
            engine.resume()
            engine.awaitQuiescent()
            val snapshot = engine.snapshot()
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), snapshot.root.status)
            assertEquals(ObjectStableId("step-3"), snapshot.root.position)

            // Clear: runs through to completion.
            engine.setBreakpoints(emptySet())
            engine.resume()
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun breakpointInsideHostedChildParksDuringStepOver() = runBlocking {
        // Under Step Over the hosted child (depth 1 > limit 0) runs free — but a breakpoint inside it must
        // still park it, Explicit, mid-step.
        val hosting = logicOf { execution ->
            execution.checkpoint()
            execution.host(ObjectStableId("child"), namedStepsLogic(10, "c"))
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(hosting, rootId, threads = 4)
        try {
            // Park at the root's first checkpoint (depth 0).
            engine.step()
            engine.awaitQuiescent()

            engine.setBreakpoints(setOf(ObjectStableId("c-5")))
            engine.step(StepMode.Over)
            engine.awaitQuiescent()

            val child = engine.snapshot().root.children.single()
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), child.status)
            assertEquals(ObjectStableId("c-5"), child.position)
            assertEquals(
                ExecutionValue.of(4L), child.live[Address.of("i")],
                "parked before element 5 ran, long before the step would have completed")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun breakpointStopsTheWorldAcrossConcurrentSpines() = runBlocking {
        // Stop-the-world: the spine that hits the breakpoint parks Explicit, and the command drops to Paused
        // so a concurrent sibling parks (Boundary) at its own next checkpoint instead of running on. The
        // sibling signals BEFORE its first boundary (a boundary could already read Paused and park it pre-
        // signal, deadlocking the test) and is then held mid-element until the breakpoint has fired.
        val reached = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val gatedChild = logicOf { execution ->
            reached.countDown()
            gate.await()
            for (i in 1 .. 10) {
                execution.checkpoint()
                execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
            }
            TupleValue.ofMain(10)
        }
        val concurrent = logicOf { execution ->
            coroutineScope {
                async { execution.host(ObjectStableId("named"), namedStepsLogic(5, "a")) }
                async { execution.host(ObjectStableId("gated"), gatedChild) }
            }
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(concurrent, rootId, threads = 4)
        try {
            engine.setBreakpoints(setOf(ObjectStableId("a-3")))
            engine.resume()

            // The gated sibling is pinned pre-loop (signalled, blocked — not parked); release it only once
            // the named spine has parked at its breakpoint, so its first checkpoint deterministically reads
            // the dropped-to-Paused command.
            reached.await()
            while (true) {
                val named = engine.snapshot().root.children.singleOrNull { it.stableId == ObjectStableId("named") }
                if (named?.status == NodeStatus.Suspended(PauseReason.Explicit)) {
                    break
                }
                delay(1)
            }
            gate.countDown()
            engine.awaitQuiescent()

            val root = engine.snapshot().root
            val named = root.children.single { it.stableId == ObjectStableId("named") }
            val gated = root.children.single { it.stableId == ObjectStableId("gated") }
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), named.status)
            assertEquals(ObjectStableId("a-3"), named.position)
            assertEquals(
                NodeStatus.Suspended(PauseReason.Boundary), gated.status,
                "the sibling parked at its first boundary instead of running on")
            assertTrue(gated.live.isEmpty(), "parked before its first emit")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun persistentBreakpointReTriggersEachLoopIteration() = runBlocking {
        // A loop re-arrives at the SAME named boundary each iteration: the breakpoint re-triggers per arrival
        // (persistent, not one-shot), while each resume still makes exactly one iteration of progress.
        val logic = logicOf { execution ->
            for (i in 1 .. 3) {
                execution.checkpoint(ObjectStableId("loop-step"))
                execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
            }
            TupleValue.ofMain(3)
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.setBreakpoints(setOf(ObjectStableId("loop-step")))
            engine.resume()
            engine.awaitQuiescent()
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), engine.snapshot().root.status)
            assertEquals(0, engine.snapshot().root.live.size, "parked before the first iteration ran")

            engine.resume()
            engine.awaitQuiescent()
            val snapshot = engine.snapshot()
            assertEquals(NodeStatus.Suspended(PauseReason.Explicit), snapshot.root.status)
            assertEquals(
                ExecutionValue.of(1L), snapshot.root.live[Address.of("i")],
                "exactly one iteration of progress per resume")

            engine.setBreakpoints(emptySet())
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(ExecutionValue.of(3L), engine.snapshot().root.live[Address.of("i")])
        }
        finally {
            engine.close()
        }
    }


    //--------------------------------------------------------------------------------------- Execution.blocking (E7 7a)
    @Test
    fun blockingCountsAsBusyForQuiescence() = runBlocking {
        // A spine parked inside Execution.blocking must read as BUSY (inFlight > 0), never falsely quiescent —
        // otherwise awaitQuiescent / migrate would proceed while blocking work is still running.
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val logic = logicOf { execution ->
            execution.blocking {
                entered.countDown()
                gate.await()
            }
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertTrue(entered.await(2, TimeUnit.SECONDS), "the blocking region should start")

            // A background awaitQuiescent must NOT return while the blocking region is held.
            val quiescent = AtomicBoolean(false)
            val waiter = Thread {
                engine.awaitQuiescent()
                quiescent.set(true)
            }
            waiter.start()
            Thread.sleep(200)
            assertFalse(quiescent.get(), "a spine inside blocking { } must read as busy, not quiescent")

            gate.countDown()
            assertIs<Outcome.Success>(engine.await())
            waiter.join(2000)
            assertTrue(quiescent.get(), "quiescence is reached once the blocking region ends")
        }
        finally {
            gate.countDown()
            engine.close()
        }
    }


    @Test
    fun cancelInterruptsBlockingSpine() = runBlocking {
        // Cancel must reach a spine parked inside blocking { }: it interrupts the elastic worker thread, which
        // surfaces as CancellationException, so the node settles Cancelled (NOT Failed) — promptly, without
        // waiting for the long blocking call to return on its own.
        val entered = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val logic = logicOf { execution ->
            execution.blocking {
                entered.countDown()
                try {
                    Thread.sleep(30_000)
                }
                catch (e: InterruptedException) {
                    interrupted.set(true)
                    throw e
                }
            }
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertTrue(entered.await(2, TimeUnit.SECONDS), "the blocking region should start")

            engine.cancel()
            val outcome = withTimeout(5000) { engine.await() }
            assertEquals(Outcome.Cancelled, outcome, "cancel interrupts a parked-in-blocking spine → Cancelled")
            assertTrue(interrupted.get(), "the elastic blocking thread was interrupted by cancel")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun cancelDuringDelayConvergesCancelled() = runBlocking {
        // Cancelling the run scope's Job now also reaches a spine in a plain cancellable suspension (delay) —
        // it converges promptly to Cancelled instead of running the suspension to completion first.
        val entered = CountDownLatch(1)
        val logic = logicOf { execution ->
            entered.countDown()
            delay(30_000)
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            engine.cancel()
            assertEquals(Outcome.Cancelled, withTimeout(5000) { engine.await() })
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------------------------ Outcome.Failed.at (E7 7c-core)
    @Test
    fun freshFailureIsStampedWithFailingNodeId() = runBlocking {
        // A fresh throwable escaping a Logic stamps Outcome.Failed.at with THAT node's own stable id.
        val engine = RunEngine(
            FlakyLogic(failBefore = 1, java.util.concurrent.atomic.AtomicInteger(), mutableListOf()), rootId)
        try {
            engine.resume()
            val failed = assertIs<Outcome.Failed>(engine.await())
            assertEquals(rootId, failed.at, "a fresh failure is stamped with the failing node's own stable id")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun failedAtPropagatesThroughHostUnchanged() = runBlocking {
        // A hosted child fails; the root's Outcome.Failed.at names the CHILD (the true origin), carried up
        // through host()'s flatten unchanged — it is NOT overwritten with the parent's own id.
        val childStableId = ObjectStableId("child")
        val child = logicOf { execution ->
            execution.recoverable({}) { throw RuntimeException("boom") }
        }
        val parent = logicOf { execution ->
            execution.host(childStableId, child)
            TupleValue.ofMain("unreached")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            val failed = assertIs<Outcome.Failed>(engine.await())
            assertEquals(childStableId, failed.at, "the failure origin propagates through host unchanged")
            assertTrue(failed.message.contains("boom"), "the failure message survives the flatten")
        }
        finally {
            engine.close()
        }
    }
}
