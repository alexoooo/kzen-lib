package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import tech.kzen.lib.common.exec.engine.ResourceScope
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
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


class RunEngineTest {
    //-----------------------------------------------------------------------------------------------------------------
    /** Emits i = 1..n, with a checkpoint *before* each emit (so a fresh pause sits before any value). */
    private class StepsLogic(private val n: Int): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            for (i in 1 .. n) {
                execution.checkpoint()
                execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
                execution.log(ExecutionValue.of("i=$i"))
            }
            return TupleValue.ofMain(n)
        }
    }


    /**
     * Counts toward [target], carrying its running count across a live edit: it adopts a captured predecessor
     * count via [Execution.restored] and exposes the current count via [Execution.onCapture]. A migration to a
     * fresh [CountUpLogic] must therefore CONTINUE from the captured count, not restart from zero.
     */
    private class CountUpLogic(private val target: Long): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            var count = execution.restored as? Long ?: 0L
            execution.onCapture { count }
            while (count < target) {
                execution.checkpoint()
                count += 1
                execution.emit(Address.of("count"), ExecutionValue.of(count))
            }
            return TupleValue.ofMain(count)
        }
    }


    /** A migration-carryable accumulator that records whether it was disposed (the removed-element case). */
    private class CloseableCounter(var count: Long): AutoCloseable {
        @Volatile
        var closed = false
            private set

        override fun close() {
            closed = true
        }
    }


    /**
     * A confined child that accumulates toward [target] in a [CloseableCounter] it adopts from its predecessor
     * (same stable id) or creates fresh. It publishes the live state object into [registry] under [id] so a test
     * can assert object identity (carried vs. fresh) across a migration.
     */
    private class CountingChildLogic(
        private val id: String,
        private val target: Long,
        private val registry: MutableMap<String, CloseableCounter>
    ): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            val state = execution.restored as? CloseableCounter ?: CloseableCounter(0)
            registry[id] = state
            execution.onCapture { state }
            while (state.count < target) {
                execution.checkpoint()
                state.count += 1
                execution.emit(Address.of("c"), ExecutionValue.of(state.count))
            }
            return TupleValue.ofMain(state.count)
        }
    }


    /** Hosts a fixed set of (stable id, child) concurrently — the parallel-children shape a migration rebuilds. */
    private class HostingLogic(private val children: List<Pair<String, Logic>>): Logic {
        override fun signature() = LogicSignature.empty

        override suspend fun run(execution: Execution): TupleValue {
            coroutineScope {
                children
                    .map { (id, logic) -> async { execution.host(ObjectStableId(id), logic) } }
                    .awaitAll()
            }
            return TupleValue.ofMain("done")
        }
    }


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


    private val rootId = ObjectStableId("root")


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
    fun hostRetainTracePropagatesToChildNode() = runBlocking {
        // A host may opt a child frame OUT of trace retention (§7 streaming bounding): the engine compacts the
        // settled frame out of the snapshot tree, and its frame-close signal carries the flag so a trace
        // consumer can evict its buffer. The default host retains: a retained child stays in the snapshot after
        // settling (post-run review). Proves the engine threads retainTrace to Node.retainTrace and acts on it.
        val hosting = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue {
                coroutineScope {
                    async { execution.host(ObjectStableId("kept"), StepsLogic(1)) }
                    async { execution.host(ObjectStableId("streamed"), StepsLogic(1), retainTrace = false) }
                }
                return TupleValue.ofMain("done")
            }
        }

        val engine = RunEngine(hosting, rootId, threads = 4)
        try {
            val closed = ConcurrentLinkedQueue<Node>()
            engine.observeFrames { closed.add(it) }

            engine.resume()
            assertTrue(engine.await() is Outcome.Success)

            val root = engine.snapshot().root
            assertTrue(root.retainTrace, "the root is always retained")
            val kept = root.children.single()
            assertEquals(ObjectStableId("kept"), kept.stableId,
                "the settled non-retained frame is compacted out; the retained one stays")
            assertTrue(kept.retainTrace, "the default host retains its child's trace")
            assertIs<NodeStatus.Terminal>(kept.status)

            val streamedClose = closed.single { it.stableId == ObjectStableId("streamed") }
            assertIs<NodeStatus.Terminal>(streamedClose.status)
            assertFalse(streamedClose.retainTrace, "an opted-out host's child frame is not retained")
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


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrateContinuesAccumulatorFromCapturedState() = runBlocking {
        // Step a root accumulator to count = 3 and park it, then edit the definition (lower the target) and
        // resume. The rebuilt logic must adopt the captured count and continue from 3 to exactly 5 — proving
        // the engine carries durable run-scoped state across a live edit by stable id, rather than restarting.
        val engine = RunEngine(CountUpLogic(100), rootId)
        try {
            repeat(4) {
                engine.step()
                engine.awaitQuiescent()
            }
            assertEquals(ExecutionValue.of(3L), engine.snapshot().root.live[Address.of("count")])
            assertTrue(engine.snapshot().root.status is NodeStatus.Suspended)

            engine.migrate(CountUpLogic(5), paused = false)
            val outcome = engine.await()

            assertEquals(5L, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
            assertEquals(ExecutionValue.of(5L), engine.snapshot().root.live[Address.of("count")])
        }
        finally {
            engine.close()
        }
    }


    //---------------------------------------------------------------- repositioning move-target carry (spec §4/§5)
    @Test
    fun migrateCarriesMoveTargetAndNextMigrateClearsIt() = runBlocking {
        // The engine carries a one-shot move target across the migration barrier, surfaced to the rebuilt tree
        // as Execution.moveTarget: null on a fresh run, the passed id on a move-migrate, and back to null on the
        // next ordinary migrate (overwrite-clears — one-shot by construction).
        val seen = mutableListOf<Any?>()
        fun parkingRoot() = logicOf { execution ->
            seen.add(execution.moveTarget)
            parkForever(execution)
        }

        val engine = RunEngine(parkingRoot(), rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertEquals(listOf<Any?>(null), seen, "a fresh run has no move target")

            engine.migrate(parkingRoot(), paused = true, moveTarget = ObjectStableId("target"))
            engine.awaitQuiescent()
            assertEquals(
                listOf<Any?>(null, ObjectStableId("target")), seen,
                "a move-migrate surfaces the target to the rebuilt tree")

            engine.migrate(parkingRoot(), paused = true)
            engine.awaitQuiescent()
            assertEquals(
                listOf<Any?>(null, ObjectStableId("target"), null), seen,
                "the next ordinary migrate overwrites the one-shot target back to null")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun migrateIgnoringMoveTargetParksLikeOrdinaryMigrate() = runBlocking {
        // The no-op contract for a non-repositionable flavour: a Logic that never reads Execution.moveTarget is
        // unaffected by a non-null target — the rebuild is an ordinary migrate. Same outcome as
        // migrateContinuesAccumulatorFromCapturedState, which passes no target.
        val engine = RunEngine(CountUpLogic(100), rootId)
        try {
            repeat(4) {
                engine.step()
                engine.awaitQuiescent()
            }
            assertEquals(ExecutionValue.of(3L), engine.snapshot().root.live[Address.of("count")])

            engine.migrate(CountUpLogic(5), paused = false, moveTarget = ObjectStableId("ignored"))
            val outcome = engine.await()

            assertEquals(
                5L, assertIs<Outcome.Success>(outcome).value.mainComponentValue(),
                "a non-repositionable Logic ignores the move target — the rebuild continues from the captured count")
            assertEquals(ExecutionValue.of(5L), engine.snapshot().root.live[Address.of("count")])
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun moveTargetReadableFromHostedChildExecution() = runBlocking {
        // The move target is tree-wide: a child hosted on the rebuilt tree reads the same Execution.moveTarget
        // the root sees (documented — a real flavour ignores an id its own structure can't resolve, but the
        // value IS visible to every node of the barrier's rebuild, since a read is not a claim).
        val target = ObjectStableId("target")
        var rootSeen: Any? = "unset"
        var childSeen: Any? = "unset"
        val engine = RunEngine(logicOf { parkForever(it) }, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            engine.migrate(
                logicOf { execution ->
                    rootSeen = execution.moveTarget
                    execution.host(
                        ObjectStableId("child"),
                        logicOf { child ->
                            childSeen = child.moveTarget
                            TupleValue.ofMain("ok")
                        })
                    parkForever(execution)
                },
                paused = true,
                moveTarget = target)
            engine.awaitQuiescent()

            assertEquals(target, rootSeen, "the rebuilt root reads the move target")
            assertEquals(target, childSeen, "a hosted child reads the same tree-wide move target")

            engine.cancel()
            engine.awaitQuiescent()
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


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrateConcurrentChildrenCarriesRenamesAddsAndDisposesRemoved() = runBlocking {
        // Two concurrent children accumulate to a parked wavefront; the edit keeps "a", removes "b", and adds
        // "c". On resume: "a" carries its accumulator object across (continued, not restarted), "c" starts fresh,
        // and "b" — claimed by no node of the rebuilt definition — is disposed as a removed-element orphan.
        val registry = HashMap<String, CloseableCounter>()
        val engine = RunEngine(
            HostingLogic(listOf(
                "a" to CountingChildLogic("a", 100, registry),
                "b" to CountingChildLogic("b", 100, registry))),
            rootId,
            threads = 4)
        try {
            repeat(4) {
                engine.step()
                engine.awaitQuiescent()
            }
            val before = engine.snapshot()
            assertEquals(2, before.root.children.size)
            assertTrue(before.root.children.all { it.status is NodeStatus.Suspended })

            val aState = registry.getValue("a")
            val bState = registry.getValue("b")
            assertTrue(aState.count > 0)

            engine.migrate(
                HostingLogic(listOf(
                    "a" to CountingChildLogic("a", 5, registry),
                    "c" to CountingChildLogic("c", 5, registry))),
                paused = false)
            val outcome = engine.await()
            assertTrue(outcome is Outcome.Success)

            // "a" carried its exact accumulator object across the edit, continuing to the new target.
            assertSame(aState, registry.getValue("a"))
            assertEquals(5L, aState.count)
            // "c" is new (added by the edit) → a fresh accumulator.
            assertNotSame(aState, registry.getValue("c"))
            assertEquals(5L, registry.getValue("c").count)
            // "b" was removed → its captured state is an orphan, disposed by the sweep at close (deferred,
            // so it is still live immediately after the rebuild).
            assertFalse(bState.closed)
            engine.close()
            assertTrue(bState.closed)
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------- capture invocation identity (spec §5)
    @Test
    fun settledInvocationCaptureCarriedOnRelaunch() = runBlocking {
        // A hosted element that COMPLETED before the barrier still carries its capture: a flavour that
        // relaunches every element on the rebuilt run (a Job worker) must let the completed one adopt its
        // "done" state instead of redoing the work (a completed reader must not re-read its file).
        val seen = mutableListOf<Any?>()
        val site = ObjectStableId("call-site")
        fun rootLogic() = logicOf { execution ->
            execution.checkpoint()
            execution.host(
                ObjectStableId("w"),
                logicOf { child ->
                    seen.add(child.restored)
                    child.onCapture { "done-state" }
                    TupleValue.ofMain("ok")
                },
                callerStableId = site)
            parkForever(execution)
        }

        val engine = RunEngine(rootLogic(), rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            engine.step()
            engine.awaitQuiescent()
            assertEquals(listOf<Any?>(null), seen, "first invocation runs fresh and completes")

            engine.migrate(rootLogic(), paused = true)
            engine.awaitQuiescent()
            engine.step()
            engine.awaitQuiescent()

            assertEquals(
                listOf<Any?>(null, "done-state"), seen,
                "the settled element's capture carries to its relaunched successor (same call-site)")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun liveInvocationCaptureWinsStableIdCollisionOverSettled() = runBlocking {
        // Two invocations of the same hosted document (same stable id, same call-site) are in the tree at the
        // barrier: an earlier one settled (retained frame), the current one mid-flight. The mid-flight frame's
        // capture must win the key collision deterministically — the resumed re-host is continuing THAT
        // invocation, not the finished one.
        val seen = mutableListOf<Any?>()
        val site = ObjectStableId("call-site")

        val original = logicOf { execution ->
            execution.host(
                ObjectStableId("c"),
                logicOf { child ->
                    child.onCapture { "settled-state" }
                    TupleValue.ofMain("first")
                },
                callerStableId = site)
            execution.host(
                ObjectStableId("c"),
                logicOf { child ->
                    child.onCapture { "live-state" }
                    parkForever(child)
                },
                callerStableId = site)
        }

        val engine = RunEngine(original, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            val edited = logicOf { execution ->
                execution.host(
                    ObjectStableId("c"),
                    logicOf { child ->
                        seen.add(child.restored)
                        TupleValue.ofMain("resumed")
                    },
                    callerStableId = site)
            }
            engine.migrate(edited, paused = false)
            engine.await()

            assertEquals(
                listOf<Any?>("live-state"), seen,
                "the mid-flight invocation's capture wins the stable-id collision over the settled one's")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun discardCapturedDropsAbandonedInvocationAndDescendants() = runBlocking {
        // A mid-flight child (and ITS mid-flight hosted grandchild) are captured at the barrier. The rebuilt
        // host discards the child's call-site BEFORE re-hosting — the loop-iteration-reset signal — so the
        // fresh invocation must read no restored state, and both discarded (unclaimed) states are closed.
        val cState = CloseableCounter(1)
        val gState = CloseableCounter(2)
        val site = ObjectStableId("run-step")
        val gSite = ObjectStableId("inner-step")
        val seen = mutableListOf<Any?>()

        val original = logicOf { execution ->
            execution.host(
                ObjectStableId("c"),
                logicOf { c ->
                    c.onCapture { cState }
                    c.host(
                        ObjectStableId("g"),
                        logicOf { g ->
                            g.onCapture { gState }
                            parkForever(g)
                        },
                        callerStableId = gSite)
                },
                callerStableId = site)
        }

        val engine = RunEngine(original, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            val edited = logicOf { execution ->
                execution.discardCaptured(listOf(site))
                execution.host(
                    ObjectStableId("c"),
                    logicOf { c ->
                        seen.add(c.restored)
                        TupleValue.ofMain("fresh")
                    },
                    callerStableId = site)
            }
            engine.migrate(edited, paused = false)
            engine.await()

            assertEquals(listOf<Any?>(null), seen, "the fresh invocation must not adopt the discarded capture")
            assertTrue(cState.closed, "the discarded unclaimed child state is closed like an orphan")
            assertTrue(gState.closed, "the grandchild's capture is discarded transitively and closed")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun capturedStateDeliveredOnlyToSameCallSite() = runBlocking {
        // Two call-sites host the same child document (same stable id). The capture taken from site A's
        // mid-flight invocation must not be delivered to a rebuilt host from site B — only to site A's.
        val state = CloseableCounter(7)
        val siteA = ObjectStableId("site-a")
        val siteB = ObjectStableId("site-b")
        val seen = mutableListOf<Any?>()

        val original = logicOf { execution ->
            execution.host(
                ObjectStableId("c"),
                logicOf { c ->
                    c.onCapture { state }
                    parkForever(c)
                },
                callerStableId = siteA)
        }

        val engine = RunEngine(original, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            val edited = logicOf { execution ->
                execution.host(
                    ObjectStableId("c"),
                    logicOf { c ->
                        seen.add(c.restored)
                        TupleValue.ofMain("b")
                    },
                    callerStableId = siteB)
                execution.host(
                    ObjectStableId("c"),
                    logicOf { c ->
                        seen.add(c.restored)
                        TupleValue.ofMain("a")
                    },
                    callerStableId = siteA)
            }
            engine.migrate(edited, paused = false)
            engine.await()

            assertEquals(
                listOf<Any?>(null, state), seen,
                "site B's invocation starts fresh; site A's adopts its own capture")
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------------ live-trace iteration reset (spec §7)
    @Test
    fun resetEmittedClearsOwnLiveAndKeepsHistory() = runBlocking {
        // The node's own live values at the given addresses are removed; other addresses and the whole
        // append-only history (emits AND logs) survive — the resettable-live / retained-history split.
        val engine = RunEngine(
            logicOf { execution ->
                execution.emit(Address.of("a"), ExecutionValue.of(1L))
                execution.emit(Address.of("b"), ExecutionValue.of(2L))
                execution.log(ExecutionValue.of("pass-1"))
                execution.resetEmitted(listOf(Address.of("a")))
                TupleValue.ofMain("ok")
            },
            rootId)
        try {
            engine.resume()
            engine.await()

            val root = engine.snapshot().root
            assertNull(root.live[Address.of("a")], "reset address removed from the live view")
            assertEquals(ExecutionValue.of(2L), root.live[Address.of("b")], "other addresses untouched")

            val history = engine.history(0)
            assertEquals(
                listOf(Address.of("a"), Address.of("b"), null),
                history.map { it.address },
                "history retains every emit and log — resets never rewrite the film-strip")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun resetEmittedClearsRetainedSettledChildSubtree() = runBlocking {
        // Settled (retained) child invocations hosted from the reset call-sites — and transitively their own
        // hosted descendants — get their live maps cleared, while the nodes stay in the tree and a sibling
        // hosted from a different call-site keeps its values.
        val siteA = ObjectStableId("site-a")
        val siteB = ObjectStableId("site-b")
        val engine = RunEngine(
            logicOf { execution ->
                execution.host(
                    ObjectStableId("c1"),
                    logicOf { c ->
                        c.emit(Address.of("x"), ExecutionValue.of(1L))
                        c.host(
                            ObjectStableId("g"),
                            logicOf { g ->
                                g.emit(Address.of("y"), ExecutionValue.of(2L))
                                TupleValue.ofMain("g")
                            })
                        TupleValue.ofMain("c1")
                    },
                    callerStableId = siteA)
                execution.host(
                    ObjectStableId("c2"),
                    logicOf { c ->
                        c.emit(Address.of("z"), ExecutionValue.of(3L))
                        TupleValue.ofMain("c2")
                    },
                    callerStableId = siteB)
                execution.resetEmitted(emptyList(), listOf(siteA))
                TupleValue.ofMain("ok")
            },
            rootId)
        try {
            engine.resume()
            engine.await()

            val root = engine.snapshot().root
            val c1 = root.children.single { it.stableId == ObjectStableId("c1") }
            val g = c1.children.single()
            val c2 = root.children.single { it.stableId == ObjectStableId("c2") }

            assertTrue(c1.live.isEmpty(), "superseded invocation's live values cleared")
            assertTrue(g.live.isEmpty(), "cleared transitively through its hosted descendants")
            assertEquals(
                ExecutionValue.of(3L), c2.live[Address.of("z")],
                "a child hosted from a different call-site is untouched")
            assertEquals(3, engine.history(0).count { it.address != null }, "history retains all three emits")
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------ transient (non-retained) emit (spec §7)
    @Test
    fun transientEmitUpdatesLiveButNotHistory() = runBlocking {
        // A non-retained emit updates the live latest-value view (with a live sequence) but is NOT appended
        // to history — a high-churn progress signal drives the live display without growing the film-strip.
        val engine = RunEngine(
            logicOf { execution ->
                execution.emit(Address.of("p"), ExecutionValue.of(1L), retain = false)
                execution.emit(Address.of("q"), ExecutionValue.of(2L))
                TupleValue.ofMain("ok")
            },
            rootId)
        try {
            engine.resume()
            engine.await()

            val root = engine.snapshot().root
            assertEquals(
                ExecutionValue.of(1L), root.live[Address.of("p")], "transient emit visible in the live view")
            assertEquals(
                ExecutionValue.of(2L), root.live[Address.of("q")], "retained emit visible in the live view")
            assertTrue(
                root.liveSequence.containsKey(Address.of("p")), "transient emit carries a live sequence")

            assertEquals(
                listOf(Address.of("q")),
                engine.history(0).map { it.address },
                "only the retained emit is appended to history; the transient one is absent")
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


    @Test
    fun resetObserverOrderingAndPayload() = runBlocking {
        // The reset signal fires synchronously BEFORE resetEmitted returns, with the superseded pass's emits
        // already drainable from history — the ordering a drain-then-clear trace bridge needs. Unsubscribing
        // stops delivery.
        val resets = ConcurrentLinkedQueue<tech.kzen.lib.common.exec.engine.TraceReset>()
        val historyAtFire = AtomicLong(-1)
        var firedBeforeReturn = false
        lateinit var subscription: AutoCloseable
        val site = ObjectStableId("cs")

        val engine = RunEngine(
            logicOf { execution ->
                execution.emit(Address.of("a"), ExecutionValue.of(1L))
                execution.resetEmitted(listOf(Address.of("a")), listOf(site))
                firedBeforeReturn = resets.isNotEmpty()
                subscription.close()
                execution.resetEmitted(listOf(Address.of("a")))
                TupleValue.ofMain("ok")
            },
            rootId)
        try {
            subscription = engine.observeResets { reset ->
                historyAtFire.set(engine.history(0).size.toLong())
                resets.add(reset)
            }
            engine.resume()
            engine.await()

            assertTrue(firedBeforeReturn, "listener invoked synchronously, before resetEmitted returns")
            assertEquals(1, resets.size, "unsubscribed listener receives no further resets")
            val reset = resets.single()
            assertEquals(rootId, reset.stableId)
            assertEquals(listOf(Address.of("a")), reset.addresses)
            assertEquals(listOf(site), reset.callSites)
            assertEquals(1L, historyAtFire.get(), "the superseded pass's emit is drainable at fire time")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun resetEmittedEmptyIsNoOp() = runBlocking {
        val resets = ConcurrentLinkedQueue<tech.kzen.lib.common.exec.engine.TraceReset>()
        val engine = RunEngine(
            logicOf { execution ->
                execution.resetEmitted(emptyList())
                TupleValue.ofMain("ok")
            },
            rootId)
        try {
            engine.observeResets { resets.add(it) }
            engine.resume()
            engine.await()
            assertTrue(resets.isEmpty(), "empty reset neither fires observers nor mutates anything")
        }
        finally {
            engine.close()
        }
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
    /** Named boundary before each element: checkpoint(step-i) then emit i, for i = 1..n. */
    private fun namedStepsLogic(n: Int, idPrefix: String = "step"): Logic =
        logicOf { execution ->
            for (i in 1 .. n) {
                execution.checkpoint(ObjectStableId("$idPrefix-$i"))
                execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
            }
            TupleValue.ofMain(n)
        }


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


    //-----------------------------------------------------------------------------------------------------------------
    // Depth of the deepest node currently Suspended (parked at a checkpoint), or -1 if none is; the root is depth 0.
    private fun deepestSuspendedDepth(node: tech.kzen.lib.common.exec.engine.Node, depth: Int = 0): Int {
        val here = if (node.status is NodeStatus.Suspended) depth else -1
        val below = node.children.maxOfOrNull { deepestSuspendedDepth(it, depth + 1) } ?: -1
        return maxOf(here, below)
    }


    //----------------------------------------------------------------------------- tree-scoped resources (ResourceScope)
    private fun logicOf(block: suspend (Execution) -> TupleValue): Logic =
        object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution) = block(execution)
        }


    // Park at checkpoints indefinitely (until cancelled or migrated away) — a [logicOf] tail for fixtures
    // that must stay live at a wavefront.
    private suspend fun parkForever(execution: Execution): Nothing {
        while (true) {
            execution.checkpoint()
        }
    }


    @Test
    fun parentScopedResourceOutlivesItsChildAndDisposesAtParentSettle() = runBlocking {
        // A child opens a Parent-scoped resource: it must survive the child's own settle and dispose when the
        // parent (here the root) settles — the "sub-script opens the SUT, the caller owns its lifetime" case.
        var disposed = false
        var disposedWhenChildReturned: Boolean? = null

        val child = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, ResourceScope.Parent) { disposed = true }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            disposedWhenChildReturned = disposed
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, disposedWhenChildReturned, "a Parent-scoped resource must outlive its child's settle")
            assertTrue(disposed, "a Parent-scoped resource is disposed when the parent settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun rootScopedResourceFromDepthTwoDisposesOnlyAtRootSettle() = runBlocking {
        // root → child → grandchild; the grandchild opens a Root-scoped resource. It must survive both the
        // grandchild's and the intermediate child's settle, and dispose only when the root run settles.
        var disposed = false
        var disposedWhenGrandchildReturned: Boolean? = null
        var disposedWhenChildReturned: Boolean? = null

        val grandchild = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, ResourceScope.Root) { disposed = true }
            TupleValue.ofMain("grandchild")
        }
        val child = logicOf { execution ->
            execution.host(ObjectStableId("grandchild"), grandchild)
            disposedWhenGrandchildReturned = disposed
            TupleValue.ofMain("child")
        }
        val root = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            disposedWhenChildReturned = disposed
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, disposedWhenGrandchildReturned, "Root-scoped resource must outlive the grandchild")
            assertEquals(false, disposedWhenChildReturned, "Root-scoped resource must outlive the intermediate child")
            assertTrue(disposed, "Root-scoped resource is disposed when the overall run settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun parentKeepOnFailureRetainsWhenParentFails() = runBlocking {
        // KeepOnFailure at Parent scope keys off the OWNING (parent) node's outcome: when the parent fails, the
        // resource is retained for inspection.
        var disposed = false
        val child = logicOf { execution ->
            execution.resource("r", ClosePolicy.KeepOnFailure, ResourceScope.Parent) { disposed = true }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            execution.recoverable({}) { throw RuntimeException("boom") }
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
            assertFalse(disposed, "keep-on-failure at Parent scope retains when the parent fails")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun parentKeepOnFailureDisposesWhenParentSucceeds() = runBlocking {
        var disposed = false
        val child = logicOf { execution ->
            execution.resource("r", ClosePolicy.KeepOnFailure, ResourceScope.Parent) { disposed = true }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertTrue(disposed, "keep-on-failure at Parent scope disposes on the parent's success")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun parentScopeAtRootFallsBackToSelf() = runBlocking {
        // Parent scope opened by the root itself (no parent) falls back to the node itself — no crash, disposed at
        // run end. Covers the "(if there is one)" clause.
        var disposed = false
        val root = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, ResourceScope.Parent) { disposed = true }
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertTrue(disposed, "Parent scope at the root falls back to self and disposes at run end")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun resourceValueReadableFromHostedChildViaAncestorWalk() = runBlocking {
        // The parent registers a resource with a live handle (value); a hosted child reads it back through the
        // ancestor-chain walk — the §6 "resource inheritance along the host chain" read affordance. A key with
        // no live registration reads null.
        var childRead: Any? = null
        var childMissing: Any? = "sentinel"
        val child = logicOf { execution ->
            childRead = execution.resourceValue("r")
            childMissing = execution.resourceValue("absent")
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "handle") {}
            execution.host(ObjectStableId("child"), child)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("handle", childRead, "a hosted child reads the handle its host registered")
            assertNull(childMissing, "a key with no live registration reads null")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun manualResourceSurvivesOwnerSettleViaParentHandUp() = runBlocking {
        // A child opens a Manual resource on its own frame and settles: the registration hands up to the
        // parent (§6 — only an explicit closing action disposes Manual, so it must outlive its owner on the
        // ancestor chain), where a LATER sibling still reads the handle and an explicit release deregisters
        // it — the engine's disposer never fires the closer (the open → use → close split across sibling
        // sub-documents pattern).
        var disposed = false
        val opener = logicOf { execution ->
            execution.resource("r", ClosePolicy.Manual, value = "handle") { disposed = true }
            TupleValue.ofMain("opened")
        }
        var siblingRead: Any? = null
        val releaser = logicOf { execution ->
            siblingRead = execution.resourceValue("r")
            execution.releaseResource("r")
            TupleValue.ofMain("released")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("opener"), opener)
            execution.host(ObjectStableId("releaser"), releaser)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("handle", siblingRead,
                "a Manual registration hands up to the parent at its owner's settle, readable by a later sibling")
            assertFalse(disposed, "explicitly released — the engine's disposer never fires a Manual closer")
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------- resource survival across live edit (spec §5)
    @Test
    fun migrateLiftsOpenResourceAndRebuiltTreeReadsIt() = runBlocking {
        // An open resource must survive the migration barrier (spec §5 "open resources"): the registration is
        // lifted off the torn-down node and re-adopted by the rebuilt node with the same stable id — the closer
        // is NOT called at teardown, the rebuilt tree reads the same handle, and the closer still fires when the
        // adopting node eventually settles.
        var disposed = false
        val opener = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "handle") { disposed = true }
            parkForever(execution)
        }
        var readBack: Any? = null
        val reader = logicOf { execution ->
            readBack = execution.resourceValue("r")
            parkForever(execution)
        }

        val engine = RunEngine(opener, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertFalse(disposed)

            engine.migrate(reader, paused = true)
            engine.awaitQuiescent()

            assertFalse(disposed, "an open resource must survive the migration barrier, not be disposed by teardown")
            assertEquals("handle", readBack, "the rebuilt tree reads the lifted + adopted resource value")

            engine.cancel()
            engine.awaitQuiescent()
            assertTrue(disposed, "the adopted registration's closer still fires when the rebuilt node settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun migrateDisposesRemovedOwnersResourceAtNextSweep() = runBlocking {
        // The edit removes the frame that owned the resource: no rebuilt node adopts it, so it lingers as an
        // orphan (deferred, like captured state) and is disposed at the next sweep (here: close) — regardless of
        // close policy (Manual), since no explicit close can reach an owner that no longer exists.
        var disposed = false
        val opener = logicOf { execution ->
            execution.resource("r", ClosePolicy.Manual, value = "handle") { disposed = true }
            parkForever(execution)
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("a"), opener)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertFalse(disposed)

            engine.migrate(logicOf { TupleValue.ofMain("edited") }, paused = false)
            assertIs<Outcome.Success>(engine.await())

            assertFalse(disposed, "a removed owner's resource lingers until the next sweep, not disposed eagerly")
            engine.close()
            assertTrue(disposed, "the orphaned resource is disposed at close, Manual policy notwithstanding")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun releaseResourceFromDescendantDeregistersAdoptedRegistration() = runBlocking {
        // After a migrate, the adopted registration behaves like any live one: a descendant of the adopting node
        // can release it (ancestor-chain walk), so the auto-disposer never fires it.
        var disposed = false
        val opener = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "handle") { disposed = true }
            parkForever(execution)
        }
        val releaser = logicOf { execution ->
            execution.releaseResource("r")
            TupleValue.ofMain("released")
        }
        val rebuilt = logicOf { execution ->
            execution.host(ObjectStableId("releaser"), releaser)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(opener, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            engine.migrate(rebuilt, paused = false)
            assertIs<Outcome.Success>(engine.await())

            engine.close()
            assertFalse(disposed, "a released adopted resource is neither auto-disposed nor swept")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun releaseResourceFromDescendantRemovesAncestorScopedRegistration() = runBlocking {
        // A resource handed up to the parent by one child can be deregistered by a sibling child: releaseResource
        // walks the caller's ancestor chain, finds it on the parent, and removes it — so the auto-disposer never
        // fires it.
        var disposed = false
        val opener = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, ResourceScope.Parent) { disposed = true }
            TupleValue.ofMain("opener")
        }
        val releaser = logicOf { execution ->
            execution.releaseResource("r")
            TupleValue.ofMain("releaser")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("opener"), opener)
            execution.host(ObjectStableId("releaser"), releaser)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertFalse(disposed, "an ancestor-scoped registration released by a descendant is not auto-disposed")
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
