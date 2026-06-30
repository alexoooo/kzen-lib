package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
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
}
