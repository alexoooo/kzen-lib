package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.engine.resource.Held
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * Frames as holders (logic-spec §6): value holds, the host boundary that hands a result over live, the root
 * that has no receiver, retention of what a settle cannot release, detach, migration of holds and the live
 * view. The settlement table itself is pinned in [RunEngineContextTest].
 */
class RunEngineHoldTest {
    private val adapters = DefaultDataAdapterRegistry()


    private fun bindingsOf(value: DataValue): DataBindings =
        DataBindings.bind(mainBindingSchema, mainBindingName to value)


    @Test
    fun aChildsReturnedOwnedValueIsLiveInTheParentAndClosesAtTheParentsSettle() = runBlocking {
        val resource = CloseableCounter(1)
        var closedWhenChildReturned: Boolean? = null
        var parentSawOwners: Boolean? = null
        var readInParent: Any? = null

        val child = logicOf { execution ->
            val held = assertNotNull(execution.hold(adapters.lift(resource)))
            bindingsOf(held.value)
        }
        val parent = logicOf { execution ->
            val returned = execution.host(ObjectStableId("child"), child).requireValue(mainBindingName)
            closedWhenChildReturned = resource.closed
            parentSawOwners = returned.owners != null
            readInParent = returned.native()
            bindingsOfMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, closedWhenChildReturned, "the parent held it before the child settled")
            assertEquals(true, parentSawOwners)
            assertSame(resource, readInParent)
            assertTrue(resource.closed, "nothing holds it once the parent settles")
            assertTrue(engine.liveResources().isEmpty())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aChildsUnrecordedReturnedCloseableIsAdoptedAtTheHostBoundary() = runBlocking {
        val resource = CloseableCounter(1)
        var ownedInParent: Boolean? = null

        val child = logicOf { bindingsOf(adapters.lift(resource)) }
        val parent = logicOf { execution ->
            val returned = execution.host(ObjectStableId("child"), child).requireValue(mainBindingName)
            ownedInParent = returned.owners != null && !resource.closed
            bindingsOfMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(true, ownedInParent, "a closeable that crosses the boundary is tracked from there")
            assertTrue(resource.closed)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun theRootsReturnedOwnedValueClosesAtTheRootsSettle() = runBlocking {
        val resource = CloseableCounter(1)
        var closedBeforeReturn: Boolean? = null
        val logic = logicOf { execution ->
            val held = assertNotNull(execution.hold(adapters.lift(resource)))
            closedBeforeReturn = resource.closed
            bindingsOf(held.value)
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            val success = assertIs<Outcome.Success>(engine.await())
            assertEquals(false, closedBeforeReturn)
            assertTrue(resource.closed, "no frame is left to hold it")
            assertNotNull(success.value.requireValue(mainBindingName).owners)
            assertFailsWith<DataAccessException> { success.value.requireValue(mainBindingName).native() }
            assertTrue(engine.retainedBindings().isEmpty())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun reRecordingASlotReleasesThePreviousHoldAndTwoHoldersCloseAtTheSecondRelease() = runBlocking {
        val first = CloseableCounter(1)
        val second = CloseableCounter(2)
        val closedAfterReRecord = ArrayList<Boolean>()
        var secondHolderStillOpen: Boolean? = null

        val logic = logicOf { execution ->
            var slot: Held? = execution.hold(adapters.lift(first))
            // A loop body recording the same slot: the previous hold goes, so the superseded value closes
            slot?.hold?.release()
            closedAfterReRecord.add(first.closed)
            slot = execution.hold(adapters.lift(second))
            // A second frame hold on the same value: the first release closes nothing
            val again = assertNotNull(execution.hold(slot!!.value))
            again.hold.release()
            secondHolderStillOpen = !second.closed
            bindingsOfMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(listOf(true), closedAfterReRecord)
            assertEquals(true, secondHolderStillOpen)
            assertTrue(second.closed, "the frame's own hold is the last and goes at settle")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun supersessionExplicitReleaseAndSettleRaceToOneClose() = runBlocking {
        val resource = CloseableCounter(1)
        var closes = 0
        val key = ContextKey.of("r")
        var openAfterRelease: Boolean? = null

        val logic = logicOf { execution ->
            // One entry: the binding's closer is what closes it, the value hold attaches the same entry
            execution.bind(key, resource, FrameDisposal(ClosePolicy.Auto) { closes++; resource.close() })
            val held = assertNotNull(execution.hold(adapters.lift(resource)))
            execution.bind(key, resource, FrameDisposal(ClosePolicy.Auto) { closes += 100 })
            execution.releaseBinding(key)
            openAfterRelease = closes == 0 && held.hold.isActive
            assertEquals(1, engine().liveResources().size)
            bindingsOfMain("done")
        }

        val engine = RunEngine(logic, rootId)
        engineRef = engine
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(true, openAfterRelease, "the value hold keeps it open past re-bind and release")
            assertEquals(1, closes, "the FIRST adoption's closer runs, exactly once, at the frame's settle")
            assertTrue(resource.closed)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun releaseResourceDetachesSoNoCloserRunsAndACoHolderReadsFailByName() = runBlocking {
        val resource = CloseableCounter(1)
        var closerRan = false
        var readFailed: Boolean? = null
        var releaseAfterDetachClosed: Boolean? = null

        val logic = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, resource) { closerRan = true }
            val held = assertNotNull(execution.hold(adapters.lift(resource)))
            execution.releaseResource("r")
            readFailed = runCatching { held.value.native() }.exceptionOrNull() is DataAccessException
            held.hold.release()
            releaseAfterDetachClosed = resource.closed
            bindingsOfMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertFalse(closerRan, "the caller declared it closed the native itself")
            assertEquals(true, readFailed)
            assertEquals(false, releaseAfterDetachClosed, "a co-holder's release after detach is a no-op")
            assertFalse(resource.closed)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun manualHandUpOntoAnOccupiedKeyAndAnonymousKeepOnFailureBothLandInRetained() = runBlocking {
        val key = ContextKey.of("m")
        val disposed = ArrayList<String>()

        val child = logicOf { execution ->
            execution.bind(key, "child", FrameDisposal(ClosePolicy.Manual) { disposed.add("child-manual") })
            bindingsOfMain("child")
        }
        val parent = logicOf { execution ->
            execution.bind(key, "parent", FrameDisposal(ClosePolicy.Auto) { disposed.add("parent-auto") })
            execution.host(ObjectStableId("child"), child)
            execution.onSettle(SettleDisposalPolicy.KeepOnFailure) { disposed.add("anonymous-keep") }
            execution.recoverable({}) { throw RuntimeException("boom") }
            bindingsOfMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
            assertEquals(listOf("parent-auto"), disposed)
            val retained = engine.retainedBindings()
            assertEquals(setOf(key, null), retained.map { it.key }.toSet(),
                "the displaced hand-up and the anonymous keep-on-failure are both findable")
            assertEquals(2, engine.liveResources().size, "live shows exactly what retained can reach")

            engine.close()
            assertEquals(2, engine.retainedBindings().size, "retained survives dispose")
            for (binding in retained) {
                assertTrue(engine.releaseRetained(binding.node, binding.key))
            }
            assertEquals(setOf("parent-auto", "child-manual", "anonymous-keep"), disposed.toSet())
            assertTrue(engine.liveResources().isEmpty())
            assertTrue(engine.retainedBindings().isEmpty())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun migrateCarriesFrameHoldsAndReleasesAnUnclaimedOnesOnce() = runBlocking {
        val carried = CloseableCounter(1)
        val orphaned = CloseableCounter(2)
        var liveAfterEdit: Int? = null

        val opener = logicOf { execution ->
            execution.hold(adapters.lift(carried))
            execution.host(ObjectStableId("removed"), logicOf { child ->
                child.hold(adapters.lift(orphaned))
                parkForever(child)
            })
            parkForever(execution)
        }
        val reader = logicOf { execution ->
            liveAfterEdit = engine().liveResources().size
            parkForever(execution)
        }

        val engine = RunEngine(opener, rootId)
        engineRef = engine
        try {
            engine.step()
            engine.awaitQuiescent()
            engine.step()
            engine.awaitQuiescent()
            assertEquals(2, engine.liveResources().size)

            engine.migrate(reader, paused = true, removedStableIds = setOf(ObjectStableId("removed")))
            engine.awaitQuiescent()
            assertFalse(carried.closed, "the root's hold rides to the rebuilt root")
            assertFalse(orphaned.closed, "an orphan lingers until the next sweep")
            assertEquals(2, liveAfterEdit)

            engine.migrate(reader, paused = true)
            engine.awaitQuiescent()
            assertTrue(orphaned.closed, "the removed frame's hold is released by the sweep")
            assertFalse(carried.closed)

            engine.cancel()
            engine.awaitQuiescent()
            assertTrue(carried.closed)
            assertTrue(engine.liveResources().isEmpty())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun theSnapshotShowsOpenResourcesWithHoldsByHolder() = runBlocking {
        val resource = CloseableCounter(1)
        var during: List<tech.kzen.lib.common.exec.engine.ResourceState>? = null

        val logic = logicOf { execution ->
            execution.hold(adapters.lift(resource))
            execution.onSettle(SettleDisposalPolicy.Auto) {}
            during = engine().snapshot().resources
            bindingsOfMain("done")
        }

        val engine = RunEngine(logic, rootId)
        engineRef = engine
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            val states = assertNotNull(during)
            assertEquals(
                setOf(CloseableCounter::class.java.name, "anonymous"), states.map { it.description }.toSet())
            for (state in states) {
                assertEquals(mapOf("root@n0" to 1), state.holds)
                assertFalse(state.borrowed)
            }
            assertTrue(engine.snapshot().resources.isEmpty())
            assertNull(engine.snapshot().resources.firstOrNull())
        }
        finally {
            engine.close()
        }
    }


    // The engine under test, for a Logic that inspects the run it is part of.
    private var engineRef: RunEngine? = null

    private fun engine(): RunEngine = engineRef!!
}
