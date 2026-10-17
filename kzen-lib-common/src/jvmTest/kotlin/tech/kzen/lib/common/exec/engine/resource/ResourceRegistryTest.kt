package tech.kzen.lib.common.exec.engine.resource

import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * The resource primitives (logic-spec §6): one entry per native, attach over re-adoption, hold counting across
 * holders, close ordering of a derived value's owners, borrowed entries, detach, concurrent final releases and
 * the unowned fast path. Frame behaviour (settle, supersession, the host boundary) is the engine tests'.
 */
class ResourceRegistryTest {
    private val worker = Holder("main.workers/sort")
    private val channel = Holder("main.channels/items")
    private val producer = Holder("producer")


    class Wrapper(val resource: CloseCountingResource)


    @Test
    fun aFreshValueOverAnOwnedNativeAttachesTheExistingEntry() {
        CloseCountingResource.reset()
        val registry = ResourceRegistry()
        val resource = CloseCountingResource("r")
        DefaultDataAdapterRegistry().use { adapters ->
            val first = assertNotNull(registry.hold(adapters.lift(resource), producer))
            val entry = assertNotNull(registry.entryOf(resource))
            assertSame(resource, entry.native)
            assertTrue(entry in assertNotNull(first.value.owners))

            // A second lift of the same native is a second value over one entry: no second adoption, two holders
            val second = assertNotNull(registry.hold(adapters.lift(resource), channel))
            assertSame(entry, assertNotNull(second.value.owners).entries().single())
            assertEquals(mapOf(producer to 1, channel to 1), entry.holds())
            assertEquals(1, registry.live().size)

            first.hold.release()
            first.hold.release()
            assertFalse(resource.isClosed, "one hold remains")
            second.hold.release()
            assertTrue(resource.isClosed)
            assertEquals(1, resource.closeCount)
            assertNull(registry.entryOf(resource), "a closed entry leaves the index")
            assertTrue(registry.live().isEmpty())

            // A read through the closed owner fails by name; the unowned path never checks
            val error = assertFailsWith<DataAccessException> { second.value.native() }
            assertTrue(error.problem.message.contains("closed"), error.problem.message)
            assertFailsWith<IllegalStateException> { entry.hold(worker) }
        }
    }


    @Test
    fun anUnownedValueCostsNothingAndAScalarIsNeverAdopted() {
        val registry = ResourceRegistry()
        DefaultDataAdapterRegistry().use { adapters ->
            val text = adapters.lift("text")
            assertNull(text.owners)
            assertNull(registry.hold(text, producer))
            val plain = adapters.lift(Wrapper(CloseCountingResource("inner")))
            assertNull(registry.hold(plain, producer), "only the ROOT native's closeability counts")
            assertTrue(registry.live().isEmpty())
            text.requireOpen()
            assertEquals("text", text.native())
        }
    }


    @Test
    fun aDerivedValueClosesItsOwnEntryBeforeItsInheritedOnes() {
        CloseCountingResource.reset()
        val registry = ResourceRegistry()
        val parent = CloseCountingResource("parent")
        val child = CloseCountingResource("child")
        DefaultDataAdapterRegistry().use { adapters ->
            val parentHeld = assertNotNull(registry.hold(adapters.lift(parent), producer))
            // A child derived from the parent inherits its owners; its own entry comes first
            val derived = adapters.lift(child).withOwners(parentHeld.value.owners)
            val childHeld = assertNotNull(registry.hold(derived, worker))
            assertEquals(
                listOf<Any?>(child, parent), childHeld.value.owners!!.entries().map { it.native })

            val downstream = childHeld.value.owners!!.hold(channel)
            parentHeld.hold.release()
            childHeld.hold.release()
            assertFalse(parent.isClosed)
            assertFalse(child.isClosed)
            assertEquals(mapOf(channel to 1), registry.entryOf(parent)!!.holds())

            downstream.release()
            assertTrue(child.isClosed && parent.isClosed)
            assertEquals(listOf("child", "parent"), CloseCountingResource.closeOrder)

            // A hold on a set with a closed member takes nothing and fails by name
            val stale = assertFailsWith<IllegalStateException> { childHeld.value.owners!!.hold(channel) }
            assertTrue(stale.message!!.contains("already closed"), stale.message)
        }
    }


    @Test
    fun aBorrowedEntryIsCountedButNeverClosed() {
        val registry = ResourceRegistry()
        val shared = CloseCountingResource("shared")
        val adoption = registry.adopt(shared, null, producer)
        assertTrue(adoption.resource.isBorrowed)
        DefaultDataAdapterRegistry().use { adapters ->
            // A later hold on a value over the same native attaches the borrowed entry: no closer is ever adopted
            val held = assertNotNull(registry.hold(adapters.lift(shared), channel))
            assertSame(adoption.resource, held.value.owners!!.entries().single())
            adoption.hold.release()
            held.hold.release()
            assertFalse(shared.isClosed)
            assertFalse(adoption.resource.isClosed)
            assertEquals(1, registry.live().size, "borrowed stays visible for the run's life")

            // A value lifted outside the registry as borrowed becomes visible on its first hold
            val other = CloseCountingResource("other")
            val lifted = adapters.lift(other).withOwners(ResourceOwners.of(Resource.borrowed(other)))
            val otherHeld = assertNotNull(registry.hold(lifted, worker))
            assertSame(lifted.owners!!.entries().single(), registry.entryOf(other))
            otherHeld.hold.release()
            assertFalse(other.isClosed)
        }
    }


    @Test
    fun detachClosesWithoutRunningTheCloser() {
        val registry = ResourceRegistry()
        val resource = CloseCountingResource("detached")
        DefaultDataAdapterRegistry().use { adapters ->
            val first = assertNotNull(registry.hold(adapters.lift(resource), producer))
            val second = assertNotNull(registry.hold(adapters.lift(resource), channel))
            val entry = registry.entryOf(resource)!!

            entry.detach()
            assertTrue(entry.isClosed)
            assertEquals(0, resource.closeCount, "detach declares the native already closed by other means")
            assertNull(registry.entryOf(resource))
            assertFailsWith<DataAccessException> { second.value.native() }
            first.hold.release()
            second.hold.release()
            assertEquals(0, resource.closeCount, "a co-holder's release after detach is a no-op")
        }
    }


    @Test
    fun anAnonymousEntryRunsItsCloserOnTheLastReleaseAndThrowsItsFailureThere() {
        val registry = ResourceRegistry()
        var ran = 0
        val adoption = registry.register({ ran++ }, worker)
        val second = adoption.resource.hold(channel)
        adoption.hold.release()
        assertEquals(0, ran)
        second.release()
        assertEquals(1, ran)
        second.release()
        assertEquals(1, ran)

        val broken = registry.register({ throw IllegalStateException("close failed") }, worker)
        val failure = assertFailsWith<IllegalStateException> { broken.hold.release() }
        assertEquals("close failed", failure.message)
        assertTrue(broken.resource.isClosed, "a closer that throws still leaves the entry closed")
        assertFalse(broken.hold.isActive)
    }


    @Test
    fun concurrentFinalReleasesCloseExactlyOnce() {
        val registry = ResourceRegistry()
        val resource = CloseCountingResource("contended")
        val adoption = registry.adopt(resource, resource::close, producer)
        val threads = 16
        val holds = (1..threads).map { adoption.resource.hold(Holder("holder-$it")) }
        adoption.hold.release()
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for (hold in holds) {
                pool.execute {
                    start.await()
                    hold.release()
                    hold.release()
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }
        finally {
            pool.shutdownNow()
        }
        assertEquals(1, resource.closeCount)
        assertTrue(holds.none { it.isActive })
        assertNull(registry.entryOf(resource))
    }


    @Test
    fun holdsByHolderAggregateAcrossEntriesAndNavigationKeepsOwners() {
        val registry = ResourceRegistry()
        val a = CloseCountingResource("a")
        val b = CloseCountingResource("b")
        DefaultDataAdapterRegistry().use { adapters ->
            val heldA = assertNotNull(registry.hold(adapters.lift(a), worker))
            val heldB = assertNotNull(registry.hold(adapters.lift(b), worker))
            assertNotNull(registry.hold(heldB.value, channel))
            assertEquals(mapOf(worker to 2, channel to 1), registry.holdsByHolder())
            assertEquals(2, registry.holdsOf(worker))

            val nameNode = heldA.value.access.field(heldA.value.root, FieldId("name"))
            val child: DataValue = heldA.value.child(nameNode)
            assertSame(heldA.value.owners, child.owners)
            assertSame(heldA.value.owners, heldA.value.withMetadata(null).owners)
        }
    }
}
