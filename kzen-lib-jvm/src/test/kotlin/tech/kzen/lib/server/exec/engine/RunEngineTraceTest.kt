package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Trace observation semantics (spec §7): live-trace iteration resets, transient (non-retained) emits,
 * reset signals to trace consumers, and per-frame trace retention.
 */
class RunEngineTraceTest {
    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
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
}
