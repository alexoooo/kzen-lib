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
import tech.kzen.lib.common.exec.engine.MoveTarget
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Live-edit migration semantics of [RunEngine.migrate]: captured-state carry by stable id, repositioning
 * move-target carry, settled frames across the barrier, removed-element reports, and open-resource
 * survival across an edit.
 */
class RunEngineMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
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
        // The engine carries a one-shot move target across the migration barrier, surfaced to the frame its
        // call-site path addresses — empty here, so the rebuilt ROOT reads it: null on a fresh run, the passed
        // id on a move-migrate, and back to null on the next ordinary migrate (overwrite-clears — one-shot by
        // construction).
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

            engine.migrate(parkingRoot(), paused = true, moveTarget = MoveTarget(ObjectStableId("target")))
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

            engine.migrate(CountUpLogic(5), paused = false, moveTarget = MoveTarget(ObjectStableId("ignored")))
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


    // What a frame reads on the two move surfaces: (moveTarget, moveDescendCallSite). Asserting the pair — and
    // in host order across the whole rebuilt tree — is what separates "addressed", "transit" and "not
    // addressed"; checking either surface alone cannot tell the last two apart.
    private fun moveSurfacesOf(execution: Execution): Pair<Any?, Any?> =
        execution.moveTarget to execution.moveDescendCallSite


    // Park a fresh run at its first boundary, then rebuild it against [rebuilt] carrying [moveTarget] — the
    // barrier every move-addressing case is observed across. Returns once the rebuilt tree has quiesced and the
    // run is cancelled, so a caller only asserts what its frames recorded on the way.
    private fun acrossMoveBarrier(
        moveTarget: MoveTarget,
        rootStableId: ObjectStableId = rootId,
        rebuilt: Logic
    ) {
        val engine = RunEngine(logicOf { parkForever(it) }, rootStableId)
        try {
            engine.step()
            engine.awaitQuiescent()

            engine.migrate(rebuilt, paused = true, moveTarget = moveTarget)
            engine.awaitQuiescent()

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun moveTargetDeliveredToAddressedChildFrameWhileRootDescends() {
        // A one-hop call-site path addresses the hosted child: the child reads the target, and the root — a
        // transit frame — reads the call-site it must descend through instead of a target of its own. The
        // pairing is what lets a paused rebuild run PAST the hosting element rather than parking at it.
        val target = ObjectStableId("target")
        val site = ObjectStableId("site")
        val seen = mutableListOf<Pair<Any?, Any?>>()

        acrossMoveBarrier(
            MoveTarget(target, listOf(site)),
            rebuilt = logicOf { execution ->
                seen.add(moveSurfacesOf(execution))
                execution.host(
                    ObjectStableId("child"),
                    logicOf { child ->
                        seen.add(moveSurfacesOf(child))
                        TupleValue.ofMain("ok")
                    },
                    callerStableId = site)
                parkForever(execution)
            })

        assertEquals(
            listOf<Pair<Any?, Any?>>(null to site, target to null), seen,
            "the transit root descends through the call-site; the addressed child frame reads the target")
    }


    @Test
    fun transitFrameTakesItsPositionFromTheDescentCallSiteItSuppressed() = runBlocking {
        // A transit frame runs to its descent call-site with its own boundary SUPPRESSED, so it never
        // checkpoints there — and a named checkpoint is otherwise the only writer of a position that starts null
        // on every rebuild. Claiming the hop has to establish it, or the frame's document shows no "element
        // about to run". The frame below claims no hop and names no boundary, so it stays position-less.
        val target = ObjectStableId("target")
        val site = ObjectStableId("site")
        val workerSite = ObjectStableId("worker-site")

        val engine = RunEngine(logicOf { parkForever(it) }, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()

            engine.migrate(
                logicOf { execution ->
                    val descentCallSite = execution.moveDescendCallSite
                    execution.host(
                        ObjectStableId("worker"),
                        logicOf { worker ->
                            worker.host(
                                ObjectStableId("instruction"),
                                logicOf { parkForever(it) },
                                callerStableId = workerSite)
                        },
                        callerStableId = descentCallSite)
                    parkForever(execution)
                },
                paused = true,
                moveTarget = MoveTarget(target, listOf(site)))
            engine.awaitQuiescent()

            val root = engine.snapshot().root
            assertEquals(
                site, root.position,
                "the transit frame positions at the descent call-site it never checkpointed at")
            assertNull(
                root.children.single().position,
                "a hosting that claims no hop leaves its host position-less — a Job worker names no boundary")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun unaddressedSiblingFrameReadsNullOnBothMoveSurfaces() {
        // "Not addressed" and "IS the addressed frame" are distinct states of a frame's remaining path, not one
        // emptiness test. A sibling hosted from a call-site the path does not name reads null on BOTH surfaces,
        // while the addressed sibling reads the target — so a rebuild carries the move to one frame, never to
        // whatever else happens to be rebuilt alongside it.
        val target = ObjectStableId("target")
        val addressedSite = ObjectStableId("addressed-site")
        val otherSite = ObjectStableId("other-site")
        val seen = mutableListOf<Pair<Any?, Any?>>()

        acrossMoveBarrier(
            MoveTarget(target, listOf(addressedSite)),
            rebuilt = logicOf { execution ->
                execution.host(
                    ObjectStableId("child"),
                    logicOf { child ->
                        seen.add(moveSurfacesOf(child))
                        TupleValue.ofMain("other")
                    },
                    callerStableId = otherSite)
                execution.host(
                    ObjectStableId("child"),
                    logicOf { child ->
                        seen.add(moveSurfacesOf(child))
                        TupleValue.ofMain("addressed")
                    },
                    callerStableId = addressedSite)
                parkForever(execution)
            })

        assertEquals(
            listOf<Pair<Any?, Any?>>(null to null, target to null), seen,
            "the unaddressed sibling gets neither surface; the addressed one gets the target")
    }


    @Test
    fun moveSuffixConsumedOnceSoASecondHostingOfTheCallSiteIsNotAddressed() {
        // Consumption is one-shot: the hosting that claims a hop clears it from the parent, so a second hosting
        // from the SAME call-site in the same rebuild inherits nothing. Without it a host that re-runs its
        // call-sites would re-apply the jump on a later pass, arbitrarily far from the request that asked for it.
        val target = ObjectStableId("target")
        val site = ObjectStableId("site")
        val seen = mutableListOf<Pair<Any?, Any?>>()

        acrossMoveBarrier(
            MoveTarget(target, listOf(site)),
            rebuilt = logicOf { execution ->
                val child = logicOf { hosted ->
                    seen.add(moveSurfacesOf(hosted))
                    TupleValue.ofMain("ok")
                }
                execution.host(ObjectStableId("child"), child, callerStableId = site)
                execution.host(ObjectStableId("child"), child, callerStableId = site)
                seen.add(moveSurfacesOf(execution))
                parkForever(execution)
            })

        assertEquals(
            listOf<Pair<Any?, Any?>>(target to null, null to null, null to null), seen,
            "only the first hosting is addressed, and the parent's discharged obligation is gone with it")
    }


    @Test
    fun hostWithoutACallSiteDoesNotConsumeAMoveSuffixHop() {
        // A null callerStableId is not a wildcard: a host that names no distinct call-site cannot be
        // path-addressed, so its child reads null on both surfaces AND leaves the hop unclaimed — which the
        // properly-addressed hosting that follows still collects.
        val target = ObjectStableId("target")
        val site = ObjectStableId("site")
        val seen = mutableListOf<Pair<Any?, Any?>>()

        acrossMoveBarrier(
            MoveTarget(target, listOf(site)),
            rebuilt = logicOf { execution ->
                execution.host(
                    ObjectStableId("anonymous"),
                    logicOf { child ->
                        seen.add(moveSurfacesOf(child))
                        TupleValue.ofMain("anonymous")
                    })
                execution.host(
                    ObjectStableId("named"),
                    logicOf { child ->
                        seen.add(moveSurfacesOf(child))
                        TupleValue.ofMain("named")
                    },
                    callerStableId = site)
                parkForever(execution)
            })

        assertEquals(
            listOf<Pair<Any?, Any?>>(null to null, target to null), seen,
            "the call-site-less hosting matches nothing and leaves the hop for the named one")
    }


    @Test
    fun recursiveFramesShareAStableIdButOnlyTheAddressedOneMoves() {
        // A document hosting ITSELF: one stable id is live in four frames at once, so the target id resolves in
        // every one of them and structure alone cannot say which the user meant. The call-site path can — the
        // frame two hops down reads the target, the frames above it read only their descent obligation, and the
        // frame below reads null on both.
        val target = ObjectStableId("target")
        val site = ObjectStableId("site")
        val documentId = ObjectStableId("document")
        val deepestDepth = 3
        val seen = mutableListOf<Pair<Any?, Any?>>()

        fun selfHosting(depth: Int): Logic =
            logicOf { execution ->
                seen.add(moveSurfacesOf(execution))
                if (depth < deepestDepth) {
                    execution.host(documentId, selfHosting(depth + 1), callerStableId = site)
                }
                parkForever(execution)
            }

        acrossMoveBarrier(
            MoveTarget(target, listOf(site, site)),
            rootStableId = documentId,
            rebuilt = selfHosting(0))

        assertEquals(
            listOf<Pair<Any?, Any?>>(null to site, null to site, target to null, null to null), seen,
            "exactly the path-addressed frame moves, though all four share the document's stable id")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrateConcurrentChildrenCarriesRenamesAddsAndDisposesRemoved() = runBlocking {
        // Two concurrent children accumulate to a parked wavefront; the edit keeps "a", removes "b", and adds
        // "c". On resume: "a" carries its accumulator object across (continued, not restarted), "c" starts fresh,
        // and "b" — claimed by no node of the rebuilt definition — is disposed as a removed-element orphan.
        // Concurrent by construction: the two children publish into this from their own dispatcher threads, so
        // a plain HashMap loses an entry under an unlucky interleaving (an intermittent "Key b is missing").
        val registry = ConcurrentHashMap<String, CloseableCounter>()
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


    @Test
    fun migrateDropsARemovedElementsBreakpoint() = runBlocking {
        // Breakpoints deliberately survive a rebuild (stable-id keyed), so a removal has to clear them
        // explicitly — else the breakpoint lands on whatever the edit created at the removed step's address.
        val engine = RunEngine(namedStepsLogic(3), rootId)
        try {
            engine.setBreakpoints(setOf(ObjectStableId("step-2")))
            engine.resume()
            engine.awaitQuiescent()
            assertEquals(ObjectStableId("step-2"), engine.snapshot().root.position)

            engine.migrate(
                namedStepsLogic(3), paused = false, removedStableIds = setOf(ObjectStableId("step-2")))
            engine.awaitQuiescent()

            assertTrue(
                engine.snapshot().root.status is NodeStatus.Terminal,
                "the rebuilt run must not park at the removed element's breakpoint")
        }
        finally {
            engine.close()
        }
    }


    //--------------------------------------------------------------- settled frames across the barrier (spec §5)
    /**
     * A caller that hosts [child] under call-site [site] on its FIRST pass only, then parks — the shape of a
     * Script whose completed RunStep is replay-adopted rather than re-invoked on the rebuilt run. The rebuilt
     * definition is a fresh instance, so [hosted] carries the "already ran" fact across the barrier the way a
     * flavour's own capture would.
     */
    private fun replayAdoptingCaller(hosted: AtomicBoolean, site: ObjectStableId, child: Logic): Logic =
        logicOf { execution ->
            if (!hosted.getAndSet(true)) {
                execution.host(ObjectStableId("sub"), child, callerStableId = site)
            }
            parkForever(execution)
        }


    @Test
    fun settledChildFrameSurvivesAMigrateThatDoesNotReHostIt() = runBlocking {
        // The reported fault: a completed sub-execution (a RunStep's sub-Script) is replay-adopted rather than
        // re-invoked on the rebuilt run, so nothing re-creates its frame — and every trace query projects the
        // node tree, so the finished sub-document goes blank the moment its caller is edited.
        val hosted = AtomicBoolean(false)
        val site = ObjectStableId("call-site")
        val child = logicOf { c ->
            c.emit(Address.of("v"), ExecutionValue.of(42L))
            TupleValue.ofMain("child")
        }

        val engine = RunEngine(replayAdoptingCaller(hosted, site, child), rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertEquals(
                1, engine.snapshot().root.children.size, "the child ran and its retained frame stays in the tree")

            engine.migrate(replayAdoptingCaller(hosted, site, child), paused = true)
            engine.awaitQuiescent()

            val carried = engine.snapshot().root.children.singleOrNull()
                ?: fail("the settled frame must survive the rebuild — nothing else can re-create it")
            assertEquals(ObjectStableId("sub"), carried.stableId)
            assertEquals(site, carried.callerStableId, "call-site attribution survives with the frame")
            assertIs<NodeStatus.Terminal>(carried.status, "it is carried as settled, not resurrected as live")
            assertEquals(
                ExecutionValue.of(42L), carried.live[Address.of("v")],
                "the frame carries its live values — that IS the sub-document's execution state")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun settledChildFrameIsDroppedWhenItsCallSiteWasRemoved() = runBlocking {
        // Deleting the element that hosted a sub-execution must take that sub-execution's trace with it —
        // otherwise the deleted RunStep's sub-document lingers as navigable state with no way back to it.
        val hosted = AtomicBoolean(false)
        val site = ObjectStableId("call-site")
        val child = logicOf { TupleValue.ofMain("child") }

        val engine = RunEngine(replayAdoptingCaller(hosted, site, child), rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertEquals(1, engine.snapshot().root.children.size)

            engine.migrate(
                replayAdoptingCaller(hosted, site, child), paused = true, removedStableIds = setOf(site))
            engine.awaitQuiescent()

            assertTrue(
                engine.snapshot().root.children.isEmpty(),
                "the frame of a removed call-site's invocation is dropped, not carried")

            engine.cancel()
            engine.awaitQuiescent()
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aReHostedStableIdSupersedesItsCarriedFrame() = runBlocking {
        // A flavour that RELAUNCHES every element on the rebuilt run (a Job worker adopting its "done" state)
        // re-hosts the id the carried frame already occupies. The live re-invocation supersedes it, so an
        // editing session doesn't accumulate one stale duplicate per element per edit.
        val site = ObjectStableId("call-site")
        fun caller() = logicOf { execution ->
            execution.host(
                ObjectStableId("w"),
                logicOf { TupleValue.ofMain("ok") },
                callerStableId = site)
            parkForever(execution)
        }

        val engine = RunEngine(caller(), rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            val before = engine.snapshot().root.children.single()

            engine.migrate(caller(), paused = true)
            engine.awaitQuiescent()

            val after = engine.snapshot().root.children.single()
            assertNotEquals(
                before.id, after.id, "the relaunched invocation is the live one, not the carried frame")

            engine.cancel()
            engine.awaitQuiescent()
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
    fun migrateReDeclaresExportsAndKeepsTheLiftedResourceOnItsOwner() = runBlocking {
        // A resource carried up an export chain crosses the migration barrier by its OWNER's stable id (the
        // root here), and the rebuilt tree re-declares its exports as part of re-running each Logic.run — the
        // declarations themselves are not lifted. The lifted registration is re-adopted, still readable, and
        // still disposes at the settle of the frame it rests on. The rebuilt root exports "r" as well and
        // self-binds for want of a host, so re-declaring cannot re-home what is already bound.
        var disposed = false
        val opener = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto, value = "handle") { disposed = true }
            parkForever(execution)
        }
        val before = logicOf { execution ->
            execution.declareExport("r")
            execution.host(ObjectStableId("opener"), opener)
            parkForever(execution)
        }
        var readBack: Any? = null
        val after = logicOf { execution ->
            execution.declareExport("r")
            readBack = execution.resourceValue("r")
            parkForever(execution)
        }

        val engine = RunEngine(before, rootId)
        try {
            // step(), not resume(): [parkForever] only parks while the run is paused or stepping.
            engine.step()
            engine.awaitQuiescent()
            assertFalse(disposed, "a resource the opener exported to the root stays live while the run is parked")

            engine.migrate(after, paused = true)
            engine.awaitQuiescent()

            assertFalse(disposed, "a resource resting on the root survives the migration barrier")
            assertEquals("handle", readBack, "the rebuilt tree reads the lifted + re-adopted resource")

            engine.cancel()
            engine.awaitQuiescent()
            assertTrue(disposed, "the re-adopted registration still disposes at its resting frame's settle")
        }
        finally {
            engine.close()
        }
    }
}
