package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.async
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
import kotlin.test.assertIs
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
}
