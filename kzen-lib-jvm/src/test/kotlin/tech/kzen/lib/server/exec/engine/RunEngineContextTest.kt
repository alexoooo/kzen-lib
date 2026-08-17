package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.ContextFamily
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Context and binding semantics along a sequential host chain: exported resources and the ambient
 * binding / scoped disposal split (spec §6), call-site bootstrap bindings, and capture invocation
 * identity across a live edit (spec §5). Concurrent-sibling binding semantics are pinned in
 * [RunEngineParallelBindingTest].
 */
class RunEngineContextTest {
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


    @Test
    fun removedStableIdIsNotAdoptedByTheElementCreatedAtItsAddress() = runBlocking {
        // The edit deleted the element at "c" and created a DIFFERENT one at the same address, so the rebuilt
        // node carries the same stable id. Only the driver's removal report distinguishes the two.
        val state = CloseableCounter(1)
        val seen = mutableListOf<Any?>()

        val original = logicOf { execution ->
            execution.host(
                ObjectStableId("c"),
                logicOf { c ->
                    c.onCapture { state }
                    parkForever(c)
                })
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
                        seen.add(c.removedStableIds)
                        TupleValue.ofMain("fresh")
                    })
            }
            engine.migrate(edited, paused = false, removedStableIds = setOf(ObjectStableId("c")))
            engine.await()

            assertEquals(
                listOf<Any?>(null, setOf(ObjectStableId("c"))), seen,
                "the replacement starts fresh, and reads the removal report for state of its own")
        }
        finally {
            engine.close()
        }
        assertTrue(state.closed, "the unadopted capture is disposed as an orphan")
    }


    //----------------------------------------------------------------------------------- exported resources (spec §6)
    @Test
    fun anExportedResourceOutlivesTheOpeningChildAndDisposesWhereItComesToRest() = runBlocking {
        // The child EXPORTS "r" and opens it; the parent exports nothing, so it is the first non-exporting
        // frame and the registration rests there — outliving its opener's settle and disposing when the parent
        // settles. The "a sub-script opens the browser, the calling script owns it" case: the provider offers
        // ownership upward, the caller receives it by saying nothing.
        var disposed = false
        var disposedWhenChildReturned: Boolean? = null

        val child = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
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
            assertEquals(false, disposedWhenChildReturned, "an exported resource must outlive its opener's settle")
            assertTrue(disposed, "an exported resource is disposed when the frame it climbed to settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anUnbrokenExportChainCarriesAResourceFromDepthTwoToTheRoot() = runBlocking {
        // root → child (exports) → grandchild (exports, opens): one hop per declaration. The grandchild's
        // export carries the registration to the child, the child's own export carries it one further hop to
        // the root, and the root ends the chain by exporting nothing. So the resource survives both the
        // grandchild's and the intermediate child's settle, and disposes only when the root run settles.
        var disposed = false
        var disposedWhenGrandchildReturned: Boolean? = null
        var disposedWhenChildReturned: Boolean? = null

        val grandchild = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
            TupleValue.ofMain("grandchild")
        }
        val child = logicOf { execution ->
            execution.declareExport("r")
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
            assertEquals(false, disposedWhenGrandchildReturned, "the grandchild's export carries the resource past it")
            assertEquals(false, disposedWhenChildReturned, "the child's own export carries it past the child too")
            assertTrue(disposed, "a resource carried to the root disposes when the overall run settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun theExportChainStopsAtTheFirstNonExportingFrameEvenWhenAFurtherAncestorExports() = runBlocking {
        // The grandchild exports "r", the intermediate child does NOT, and the root does. The climb consults
        // each frame in turn and halts at the first that does not export, so the resource rests on the
        // intermediate child and dies at its settle — the root's own export is never reached. Ownership
        // travels an unbroken RUN of declarations, so it cannot tunnel through a silent frame to match a
        // further ancestor.
        var disposed = false
        var disposedWhenGrandchildReturned: Boolean? = null
        var disposedWhenChildReturned: Boolean? = null

        val grandchild = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
            TupleValue.ofMain("grandchild")
        }
        val child = logicOf { execution ->
            execution.host(ObjectStableId("grandchild"), grandchild)
            disposedWhenGrandchildReturned = disposed
            TupleValue.ofMain("child")
        }
        val root = logicOf { execution ->
            execution.declareExport("r")
            execution.host(ObjectStableId("child"), child)
            disposedWhenChildReturned = disposed
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, disposedWhenGrandchildReturned,
                "the opener's own export still carries the resource one hop, to the frame above it")
            assertEquals(true, disposedWhenChildReturned,
                "the chain halts at the first non-exporting frame, which disposes it — the exporting root never sees it")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anUnexportedProvideIsPrivateToTheOpeningNode() = runBlocking {
        // Nothing exports "r", so the resource rests on the frame that opened it and dies at that frame's
        // settle: private by default. This is what lets a sub-script keep a resource out of its caller's
        // reach, and what keeps a key nobody declares anything about working (a Job's own scratch key, a
        // plugin's private key, a raw test step).
        var disposed = false
        var disposedWhenChildReturned: Boolean? = null

        val child = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
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
            assertEquals(true, disposedWhenChildReturned,
                "an un-exported provide rests on the opening node and disposes at ITS settle")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anExportedFamilyCarriesEveryQualifiedKeyInItIndependently() = runBlocking {
        // Exporting the family "sut" carries "sut:a" and "sut:b" alike (matched on the part before the ':'
        // separator), as independent registrations with their own values and closers — the dynamic-key case
        // (one SUT per name), where the qualifier is a step parameter the declaration cannot enumerate.
        val disposed = ArrayList<String>()
        var readA: Any? = null
        var readB: Any? = null

        val child = logicOf { execution ->
            execution.declareExport("sut")
            execution.resource("sut:a", ClosePolicy.Auto, value = "handle-a") { disposed.add("a") }
            execution.resource("sut:b", ClosePolicy.Auto, value = "handle-b") { disposed.add("b") }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            readA = execution.resourceValue("sut:a")
            readB = execution.resourceValue("sut:b")
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("handle-a", readA, "a family export carries each qualified registration separately")
            assertEquals("handle-b", readB, "a family export carries each qualified registration separately")
            assertEquals(
                setOf("a", "b"), disposed.toSet(),
                "both qualified registrations dispose at the frame the family export carried them to")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun hasResourceInFamilyMatchesTheFamilyButNotTheQualifier() = runBlocking {
        // The uniform requirement gate reads at FAMILY granularity: "sut:a" being open makes the whole "sut"
        // family present, and a distinct family stays absent. A qualifier mismatch is deliberately invisible
        // here — it surfaces at read instead.
        var plainBefore: Boolean? = null
        var qualifiedFamily: Boolean? = null
        var otherFamily: Boolean? = null
        var exactPlainKey: Boolean? = null

        val child = logicOf { execution ->
            qualifiedFamily = execution.hasResourceInFamily("sut")
            otherFamily = execution.hasResourceInFamily("browser")
            exactPlainKey = execution.hasResourceInFamily("scratch")
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            plainBefore = execution.hasResourceInFamily("sut")
            execution.resource("sut:a", ClosePolicy.Auto, value = "handle") {}
            execution.resource("scratch", ClosePolicy.Auto, value = "plain") {}
            execution.host(ObjectStableId("child"), child)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, plainBefore, "nothing open yet")
            assertEquals(true, qualifiedFamily, "a qualified registration makes its family present, seen from a child")
            assertEquals(false, otherFamily, "an unrelated family stays absent")
            assertEquals(true, exactPlainKey, "an unqualified key matches its own family name exactly")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anExactExportCarriesOneMemberWhereAFamilyExportCarriesThemAll() = runBlocking {
        // The two selectors are different ownership claims, not a wide gate and a narrow one. A child that
        // declares Exact("db:primary") hands THAT member to its caller and keeps its sibling private; a child
        // that declares the whole "other" family hands up every qualifier it opens. Collapsing the first onto
        // the second would move a resource nobody offered — the leak the exact/family split exists to prevent.
        val disposed = ArrayList<String>()
        var primary: BindingLookup? = null
        var reporting: BindingLookup? = null
        var otherA: BindingLookup? = null
        var otherB: BindingLookup? = null

        val exactChild = logicOf { execution ->
            execution.declareExport(ExportSelector.Exact(ContextKey.of("db", "primary")))
            execution.resource("db:primary", ClosePolicy.Auto, value = "primary") { disposed.add("primary") }
            execution.resource("db:reporting", ClosePolicy.Auto, value = "reporting") { disposed.add("reporting") }
            TupleValue.ofMain("exact")
        }
        val familyChild = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("other")))
            execution.resource("other:a", ClosePolicy.Auto, value = "a") { disposed.add("a") }
            execution.resource("other:b", ClosePolicy.Auto, value = "b") { disposed.add("b") }
            TupleValue.ofMain("family")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("exact"), exactChild)
            execution.host(ObjectStableId("family"), familyChild)
            primary = execution.binding(ContextKey.of("db", "primary"))
            reporting = execution.binding(ContextKey.of("db", "reporting"))
            otherA = execution.binding(ContextKey.of("other", "a"))
            otherB = execution.binding(ContextKey.of("other", "b"))
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(BindingLookup.Present("primary"), primary,
                "the exactly-exported member climbs to the caller")
            assertEquals(BindingLookup.Missing, reporting,
                "a sibling qualifier the declaration does not name stays private to the frame that opened it")
            assertEquals(BindingLookup.Present("a"), otherA, "a family export carries every qualifier")
            assertEquals(BindingLookup.Present("b"), otherB, "a family export carries every qualifier")
            assertEquals(
                setOf("primary", "reporting", "a", "b"), disposed.toSet(),
                "everything is disposed exactly once, on whichever frame it came to rest")
        }
        finally {
            engine.close()
        }
    }


    @Test
    @Suppress("DEPRECATION")
    fun aBindingRegisteredWithoutAValueIsPresentRatherThanMissing() = runBlocking {
        // Presence is registration-existence, never value-non-nullness. A Context whose value contract is
        // nullable can bind null deliberately, and only the lossy plain-string read confuses that with nothing
        // being bound — which is the whole reason it is superseded.
        var boundNull: BindingLookup? = null
        var absent: BindingLookup? = null
        var lossyBound: Any? = null
        var lossyAbsent: Any? = null

        val logic = logicOf { execution ->
            execution.resource("nullable", ClosePolicy.Auto, value = null) {}
            boundNull = execution.binding(ContextKey.of("nullable"))
            absent = execution.binding(ContextKey.of("absent"))
            lossyBound = execution.resourceValue("nullable")
            lossyAbsent = execution.resourceValue("absent")
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(BindingLookup.Present(null), boundNull)
            assertEquals(BindingLookup.Missing, absent)
            assertNull(lossyBound)
            assertNull(lossyAbsent)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun exactAndFamilyPresenceGatesAnswerDifferentQuestions() = runBlocking {
        // A DECLARED qualifier can gate on the member it names; only a COMPUTED one is stuck with "is SOME sut
        // open", because no declaration-driven layer can know which member a reader will ask for.
        var exactOpen: Boolean? = null
        var exactSibling: Boolean? = null
        var familyOpen: Boolean? = null
        var otherFamily: Boolean? = null

        val logic = logicOf { execution ->
            execution.resource("sut:main", ClosePolicy.Auto, value = "handle") {}
            exactOpen = execution.hasBinding(ContextKey.of("sut", "main"))
            exactSibling = execution.hasBinding(ContextKey.of("sut", "other"))
            familyOpen = execution.hasBindingInFamily(ContextFamily("sut"))
            otherFamily = execution.hasBindingInFamily(ContextFamily("browser"))
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(true, exactOpen)
            assertEquals(false, exactSibling, "an exact gate refuses the sibling a family gate would admit")
            assertEquals(true, familyOpen, "one open qualifier makes its whole family present")
            assertEquals(false, otherFamily)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun keepOnFailureKeysOffTheRestingFramesOutcomeNotTheOpeners() = runBlocking {
        // The close policy travels with the registration and applies where it comes to rest, so KeepOnFailure
        // is evaluated at the settle of the frame the export chain handed it to: the child that opened it
        // succeeded, yet the resource is retained for inspection because the receiving frame failed.
        var disposed = false
        val child = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.KeepOnFailure) { disposed = true }
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
            assertFalse(disposed, "keep-on-failure retains when the RESTING FRAME fails, though the opener succeeded")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun keepOnFailureDisposesWhenTheRestingFrameSucceeds() = runBlocking {
        var disposed = false
        val child = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.KeepOnFailure) { disposed = true }
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
            assertTrue(disposed, "keep-on-failure disposes on the resting frame's success")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun declareExportIsIdempotent() = runBlocking {
        // Re-declaring the same export is a no-op — it is a set membership, not a counter — so a migrate
        // rebuild's re-declaration costs nothing and the chain still carries the resource exactly one hop.
        var disposed = false
        var disposedWhenChildReturned: Boolean? = null
        val child = logicOf { execution ->
            execution.declareExport("r")
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
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
            assertEquals(false, disposedWhenChildReturned, "a doubly-declared export still moves the resource one frame")
            assertTrue(disposed)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anExportingRootSelfBindsBecauseTheChainHasNoFrameToClimbTo() = runBlocking {
        // The root exports "r" and opens it, but a climb needs a host to hand ownership to and the root has
        // none: the chain terminates for want of a parent, so the registration rests on the root itself and
        // disposes when the overall run settles. An export offered to nobody is neither an error nor a leak.
        var disposed = false
        var readBack: Any? = null

        val root = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto, value = "handle") { disposed = true }
            readBack = execution.resourceValue("r")
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("handle", readBack, "an exporting root registers on its own frame and reads the handle there")
            assertTrue(disposed, "a resource resting on the root disposes when the overall run settles")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun declaringAnExportAfterARegistrationLeavesThatRegistrationWhereItRests() = runBlocking {
        // Ownership is fixed at BIND time. The child's first provide happens before it declares anything, so
        // that one rests on the child; the declaration that follows carries only the NEXT provide up to the
        // parent. The two registrations then sit on different frames under the same key, so neither supersedes
        // the other and each disposes at the settle of the frame it rests on. This is why editing a declaration
        // mid-run cannot re-home a resource that is already open.
        val disposed = ArrayList<String>()
        var disposedWhenChildReturned: List<String>? = null

        val child = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "private") { disposed.add("private") }
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto, value = "exported") { disposed.add("exported") }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            disposedWhenChildReturned = disposed.toList()
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(
                listOf("private"), disposedWhenChildReturned,
                "a registration bound before the declaration stays on the opening frame and dies at its settle")
            assertEquals(
                listOf("private", "exported"), disposed,
                "only the provide that follows the declaration climbs, so it disposes at the parent's settle")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aPrivateRegistrationShadowsAnExportedOneOfTheSameKeyWithinItsOwnSubtree() = runBlocking {
        // Two live registrations under one key are coherent. The provider exports "r", so the parent holds it;
        // a later sibling opens its own un-exported "r", which rests on that sibling's frame. The read walk is
        // self → parent → … → root and stops at the first match, so the private registration wins throughout
        // the sibling's subtree while the parent goes back to reading the exported one once the sibling
        // settles. Both closers fire — each at the settle of the frame its own registration rests on.
        var disposedExported = false
        var disposedPrivate = false
        var shadowRead: Any? = null
        var grandchildRead: Any? = null
        var disposedPrivateWhenShadowReturned: Boolean? = null
        var disposedExportedWhenShadowReturned: Boolean? = null
        var parentReadAfterShadow: Any? = null

        val provider = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto, value = "exported") { disposedExported = true }
            TupleValue.ofMain("provider")
        }
        val grandchild = logicOf { execution ->
            grandchildRead = execution.resourceValue("r")
            TupleValue.ofMain("grandchild")
        }
        val shadow = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "private") { disposedPrivate = true }
            shadowRead = execution.resourceValue("r")
            execution.host(ObjectStableId("grandchild"), grandchild)
            TupleValue.ofMain("shadow")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("provider"), provider)
            execution.host(ObjectStableId("shadow"), shadow)
            disposedPrivateWhenShadowReturned = disposedPrivate
            disposedExportedWhenShadowReturned = disposedExported
            parentReadAfterShadow = execution.resourceValue("r")
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("private", shadowRead, "the shadowing frame reads its own registration, not its host's")
            assertEquals("private", grandchildRead,
                "the shadow covers the whole subtree beneath the frame that opened it")
            assertEquals(true, disposedPrivateWhenShadowReturned,
                "the private registration disposes at its own frame's settle")
            assertEquals(false, disposedExportedWhenShadowReturned,
                "disposing the shadow leaves the exported registration on the parent untouched")
            assertEquals("exported", parentReadAfterShadow,
                "the parent reads the exported registration again once the shadowing frame is gone")
            assertTrue(disposedExported, "the exported registration disposes at the frame it climbed to")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun reRegisteringAKeyDisposesTheRegistrationItDisplaces() = runBlocking {
        // Supersession (§6): a key resolves to exactly ONE registration, so re-registering it makes the prior
        // one unreachable — it must be disposed then and there, not silently dropped. The displaced closer runs
        // AFTER the replacement is registered (the closer contract on Execution.resource), and the live
        // registration is always the newest, so a read never sees a disposed handle.
        val disposed = mutableListOf<String>()
        var handleWhileSecondLive: Any? = null

        val root = logicOf { execution ->
            execution.resource("r", ClosePolicy.Auto, value = "first") { disposed.add("first") }
            execution.resource("r", ClosePolicy.Auto, value = "second") { disposed.add("second") }
            handleWhileSecondLive = execution.resourceValue("r")
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(
                listOf("first", "second"), disposed,
                "the displaced registration is disposed at re-registration, the survivor at settle")
            assertEquals("second", handleWhileSecondLive, "the live registration is the newest one")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun reProvidingInALoopKeepsOneLiveRegistrationAndDisposesEachPredecessor() = runBlocking {
        // The regression this guards: a sub-script that re-opens a browser / subprocess every iteration used to
        // leak all but the last, because the map write dropped the prior registration without closing it. The
        // count of live-but-undisposed registrations must stay at 1 throughout, whatever the iteration count.
        val iterations = 5
        val disposed = mutableListOf<Int>()
        var registered = 0
        var peakLive = 0

        val child = logicOf { execution ->
            // The child EXPORTS the key, so every iteration's provide climbs to the root and collides with its
            // predecessor THERE — which is the shape a re-providing sub-script actually has.
            execution.declareExport("r")
            val iteration = registered++
            execution.resource("r", ClosePolicy.Auto, value = iteration) { disposed.add(iteration) }
            peakLive = maxOf(peakLive, registered - disposed.size)
            TupleValue.ofMain("child")
        }
        val root = logicOf { execution ->
            repeat(iterations) {
                execution.host(ObjectStableId("child"), child)
            }
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(1, peakLive, "at most one registration under a key is live at a time")
            assertEquals((0 until iterations).toList(), disposed, "every iteration's resource is disposed once")
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


    @Test
    fun manualHandsUpPastTheChainsRestingFrameAtThatFramesSettle() = runBlocking {
        // The export chain and Manual are orthogonal, and both may apply to one resource: the opener exports
        // "r", so the registration rests one frame up (the intermediate node exports nothing), and at THAT
        // frame's settle the Manual hand-up walks it one level further — where the root reads and releases it.
        // The chain moves ownership at bind time; Manual separately lets a registration outlive the frame it
        // rests on, which is the only way an un-exported resource reaches a caller at all.
        var disposed = false
        var rootRead: Any? = null

        val opener = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Manual, value = "handle") { disposed = true }
            TupleValue.ofMain("opener")
        }
        val restingFrame = logicOf { execution ->
            execution.host(ObjectStableId("opener"), opener)
            TupleValue.ofMain("restingFrame")
        }
        val root = logicOf { execution ->
            execution.host(ObjectStableId("restingFrame"), restingFrame)
            rootRead = execution.resourceValue("r")
            execution.releaseResource("r")
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals("handle", rootRead,
                "a Manual registration hands up past the frame it rests on at that frame's settle")
            assertFalse(disposed, "explicitly released — the engine never fires a Manual closer")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun releaseResourceFromDescendantRemovesAnExportedRegistration() = runBlocking {
        // A resource one child exported up to the parent can be deregistered by a sibling child:
        // releaseResource walks the caller's ancestor chain, finds it on the frame it rests on, and removes it
        // — so the auto-disposer never fires it. The open → use → close split across sibling sub-documents.
        var disposed = false
        val opener = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto) { disposed = true }
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
            assertFalse(disposed, "an exported registration released by a descendant is not auto-disposed")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun releaseFromBelowReachesARegistrationCarriedUpALongerExportChain() = runBlocking {
        // The registration climbs two hops (the opener exports, the intermediate frame re-exports, the root
        // does not) and is then released by a frame the chain never touched. The release walk is the same
        // self → parent → … → root walk as the read, so how far a registration was carried is irrelevant to
        // who can deregister it: any descendant of the frame it rests on reaches it.
        var disposed = false
        var disposedWhenMidReturned: Boolean? = null
        var releaserRead: Any? = null

        val opener = logicOf { execution ->
            execution.declareExport("r")
            execution.resource("r", ClosePolicy.Auto, value = "handle") { disposed = true }
            TupleValue.ofMain("opener")
        }
        val mid = logicOf { execution ->
            execution.declareExport("r")
            execution.host(ObjectStableId("opener"), opener)
            TupleValue.ofMain("mid")
        }
        val releaser = logicOf { execution ->
            releaserRead = execution.resourceValue("r")
            execution.releaseResource("r")
            TupleValue.ofMain("releaser")
        }
        val root = logicOf { execution ->
            execution.host(ObjectStableId("mid"), mid)
            disposedWhenMidReturned = disposed
            execution.host(ObjectStableId("releaser"), releaser)
            TupleValue.ofMain("root")
        }

        val engine = RunEngine(root, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(false, disposedWhenMidReturned,
                "two export hops carry the resource clear of the whole providing subtree, up to the root")
            assertEquals("handle", releaserRead, "a frame below the resting one reads it through the ancestor walk")
            assertFalse(disposed, "a registration released from below the resting frame is never auto-disposed")
        }
        finally {
            engine.close()
        }
    }


    //--------------------------------------------------------- ambient binding / scoped disposal split (spec §6)
    // The settlement table: what each event does to a managed binding under each policy, and to an anonymous
    // disposal under the two that can apply to one. Every cell is pinned here rather than described, because
    // the previous fused implementation left several of them (root/manual, failed keep-on-failure) as
    // "the registration disappears" — which reads as retention only until someone looks for what was retained.
    private val autoKey = ContextKey.of("auto")
    private val manualKey = ContextKey.of("manual")
    private val keepKey = ContextKey.of("keep")


    private fun Execution.bindAllPolicies(disposed: MutableList<String>) {
        bind(autoKey, "a", FrameDisposal(ClosePolicy.Auto) { disposed.add("auto") })
        bind(manualKey, "m", FrameDisposal(ClosePolicy.Manual) { disposed.add("manual") })
        bind(keepKey, "k", FrameDisposal(ClosePolicy.KeepOnFailure) { disposed.add("keep") })
    }


    @Test
    fun aPlainBindingGoesOutOfScopeWithNothingTornDown() = runBlocking {
        // A value that closes nothing no longer has to register an empty closer to say so — which is the
        // conflation the split removes. Releasing one degenerates to unbinding the name.
        val baseKey = ContextKey.of("base")
        var childRead: BindingLookup? = null
        var childAfterRelease: BindingLookup? = null
        var parentRead: BindingLookup? = null

        val child = logicOf { execution ->
            execution.bind(baseKey, "C:/work/inbox")
            childRead = execution.binding(baseKey)
            execution.bind(baseKey, "C:/work/outbox")
            execution.releaseBinding(baseKey)
            childAfterRelease = execution.binding(baseKey)
            execution.bind(baseKey, "C:/work/inbox")
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            parentRead = execution.binding(baseKey)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(BindingLookup.Present("C:/work/inbox"), childRead)
            assertEquals(BindingLookup.Missing, childAfterRelease,
                "releasing an unmanaged binding removes the name; there is nothing to dispose")
            assertEquals(BindingLookup.Missing, parentRead, "an un-exported binding is private to its frame")
            assertTrue(engine.retainedBindings().isEmpty(), "nothing unmanaged can be retained")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun supersessionReleaseAndSettleEachDisposeExactlyOnce() = runBlocking {
        // At-most-once is structural: all three events route through the same one-shot claim, so a closer is
        // written for a single invocation instead of being made defensively idempotent.
        val key = ContextKey.of("r")
        val disposed = ArrayList<String>()
        var afterRebind: BindingLookup? = null
        var afterRelease: BindingLookup? = null

        val logic = logicOf { execution ->
            execution.bind(key, "first", FrameDisposal(ClosePolicy.Auto) { disposed.add("first") })
            execution.bind(key, "second", FrameDisposal(ClosePolicy.Auto) { disposed.add("second") })
            afterRebind = execution.binding(key)
            execution.releaseBinding(key)
            afterRelease = execution.binding(key)
            execution.releaseBinding(key)
            execution.bind(key, "third", FrameDisposal(ClosePolicy.Auto) { disposed.add("third") })
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(BindingLookup.Present("second"), afterRebind)
            assertEquals(BindingLookup.Missing, afterRelease, "release removes the name, not only the teardown")
            assertEquals(
                listOf("first", "second", "third"), disposed,
                "supersession, explicit release and settle each dispose once, and a second release is a no-op")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun explicitReleaseDisposesWhateverThePolicyWouldHaveDoneAtSettle() = runBlocking {
        val disposed = ArrayList<String>()

        val logic = logicOf { execution ->
            execution.bindAllPolicies(disposed)
            execution.releaseBinding(autoKey)
            execution.releaseBinding(manualKey)
            execution.releaseBinding(keepKey)
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(
                listOf("auto", "manual", "keep"), disposed,
                "the policy governs what SETTLE does; an explicit release always tears down")
            assertTrue(engine.retainedBindings().isEmpty())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aSucceedingNonRootFramePromotesManualAndDisposesTheRest() = runBlocking {
        val disposed = ArrayList<String>()
        var disposedWhenChildReturned: Set<String>? = null
        var promotedReadInParent: BindingLookup? = null

        val child = logicOf { execution ->
            execution.bindAllPolicies(disposed)
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            disposedWhenChildReturned = disposed.toSet()
            promotedReadInParent = execution.binding(manualKey)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(setOf("auto", "keep"), disposedWhenChildReturned)
            assertEquals(
                BindingLookup.Present("m"), promotedReadInParent,
                "manual is promoted one frame up, so a later sibling can still find and close it")
            assertEquals(
                listOf(manualKey), engine.retainedBindings().map { it.key },
                "the promoted binding reaches the root and is retained there, never silently dropped")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aFailingNonRootFrameRetainsKeepOnFailureAndStillDisposesAuto() = runBlocking {
        val disposed = ArrayList<String>()

        val child = logicOf { execution ->
            execution.bind(autoKey, "a", FrameDisposal(ClosePolicy.Auto) { disposed.add("auto") })
            execution.bind(keepKey, "k", FrameDisposal(ClosePolicy.KeepOnFailure) { disposed.add("keep") })
            execution.recoverable({}) { throw RuntimeException("boom") }
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
            assertEquals(listOf("auto"), disposed, "keep-on-failure is held on the frame that failed")
            assertEquals(
                listOf(keepKey), engine.retainedBindings().map { it.key },
                "retention is real: the binding stays findable instead of vanishing with its frame")
            assertEquals("k", engine.retainedBindings().single().value)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aSucceedingRootRetainsManualUninvokedAndDisposesTheRest() = runBlocking {
        val disposed = ArrayList<String>()

        val logic = logicOf { execution ->
            execution.bindAllPolicies(disposed)
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(setOf("auto", "keep"), disposed.toSet())
            assertEquals(
                listOf(manualKey), engine.retainedBindings().map { it.key },
                "at the root there is nowhere to promote to, so manual is kept uninvoked — the forgotten close")
            assertEquals("m", engine.retainedBindings().single().value)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aFailingRootRetainsBothManualAndKeepOnFailure() = runBlocking {
        val disposed = ArrayList<String>()

        val logic = logicOf { execution ->
            execution.bindAllPolicies(disposed)
            execution.recoverable({}) { throw RuntimeException("boom") }
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
            assertEquals(listOf("auto"), disposed)
            assertEquals(
                setOf(manualKey, keepKey), engine.retainedBindings().map { it.key }.toSet(),
                "a failed root keeps both, and both stay closeable")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun cancelSettlesLikeSuccess() = runBlocking {
        // The table pairs success and cancel: neither is a failure, so only manual survives.
        val disposed = ArrayList<String>()

        val logic = logicOf { execution ->
            execution.bindAllPolicies(disposed)
            parkForever(execution)
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            engine.cancel()
            assertEquals(Outcome.Cancelled, withTimeout(5000) { engine.await() })
            assertEquals(setOf("auto", "keep"), disposed.toSet())
            assertEquals(listOf(manualKey), engine.retainedBindings().map { it.key })
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aRetainedBindingIsCloseableExactlyOnce() = runBlocking {
        // What makes "retain" mean something rather than "silently drop": the run can still name what it kept,
        // and closing it goes through the same one-shot claim as every other disposal.
        var disposals = 0

        val logic = logicOf { execution ->
            execution.bind(manualKey, "handle", FrameDisposal(ClosePolicy.Manual) { disposals += 1 })
            TupleValue.ofMain("done")
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())

            val retained = engine.retainedBindings().single()
            assertEquals("handle", retained.value)
            assertEquals(0, disposals)

            assertTrue(engine.releaseRetained(retained.node, retained.key))
            assertEquals(1, disposals)
            assertTrue(engine.retainedBindings().isEmpty())

            assertFalse(engine.releaseRetained(retained.node, retained.key), "nothing is retained there now")
            assertEquals(1, disposals)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anAnonymousDisposalIsFrameLocalAndNeverClimbs() = runBlocking {
        // Ownership transfer requires a name: the child's BINDING climbs to the parent on its export, while its
        // anonymous cleanup has nothing to hand upward and settles with the child.
        val order = ArrayList<String>()
        var anonymousRanWhenChildReturned: Boolean? = null

        val child = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("r")))
            execution.bind(
                ContextKey.of("r"), "handle", FrameDisposal(ClosePolicy.Auto) { order.add("binding") })
            execution.onSettle(SettleDisposalPolicy.Auto) { order.add("anonymous") }
            TupleValue.ofMain("child")
        }
        val parent = logicOf { execution ->
            execution.host(ObjectStableId("child"), child)
            anonymousRanWhenChildReturned = "anonymous" in order
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(true, anonymousRanWhenChildReturned, "an anonymous disposal settles with its own frame")
            assertEquals(
                listOf("anonymous", "binding"), order,
                "the exported binding outlives the frame that opened it; the anonymous cleanup does not")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anAnonymousKeepOnFailureLeavesItsSideEffectUndone() = runBlocking {
        // There is no handle to retain here — retention means the temp file simply stays for inspection — so
        // the closer is never claimed rather than parked somewhere.
        val ran = ArrayList<String>()

        val logic = logicOf { execution ->
            execution.onSettle(SettleDisposalPolicy.Auto) { ran.add("auto") }
            execution.onSettle(SettleDisposalPolicy.KeepOnFailure) { ran.add("keep") }
            execution.recoverable({}) { throw RuntimeException("boom") }
        }

        val engine = RunEngine(logic, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
            assertEquals(listOf("auto"), ran)
            assertTrue(engine.retainedBindings().isEmpty(), "an anonymous registration has no key to retain it by")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun migrationCarriesBothRegistriesWithoutSettlingThem() = runBlocking {
        val disposed = ArrayList<String>()
        var readBack: BindingLookup? = null

        val opener = logicOf { execution ->
            execution.bind(
                ContextKey.of("r"), "handle", FrameDisposal(ClosePolicy.Auto) { disposed.add("binding") })
            execution.onSettle(SettleDisposalPolicy.Auto) { disposed.add("anonymous") }
            parkForever(execution)
        }
        val reader = logicOf { execution ->
            readBack = execution.binding(ContextKey.of("r"))
            parkForever(execution)
        }

        val engine = RunEngine(opener, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertTrue(disposed.isEmpty())

            engine.migrate(reader, paused = true)
            engine.awaitQuiescent()

            assertTrue(disposed.isEmpty(), "the barrier lifts both registries; it does not settle them")
            assertEquals(BindingLookup.Present("handle"), readBack,
                "the rebuilt frame with the same stable id adopts the carried binding")

            engine.cancel()
            engine.awaitQuiescent()
            assertEquals(
                listOf("anonymous", "binding"), disposed,
                "an anonymous registration carries across the edit exactly as a named binding does")
        }
        finally {
            engine.close()
        }
    }


    //------------------------------------------------------------------- call-site bootstrap bindings (spec §6)
    @Test
    fun aBootstrapBindingIsVisibleToTheChildsFirstInstruction() = runBlocking {
        // An ambient dependency the caller supplies has to be in scope before ANY of the callee's own code
        // runs: [Execution.host] mints the child frame and runs it as one operation, so there is no instant at
        // which the caller could bind it instead — and a value that only arrived once the callee yielded would
        // already be too late for the first step it exists to serve.
        var firstRead: BindingLookup? = null

        val callee = logicOf { execution ->
            firstRead = execution.binding(ContextKey.of("sut"))
            parkForever(execution)
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(InitialBinding(ContextKey.of("sut"), "handleA")))
            parkForever(execution)
        }

        val engine = RunEngine(caller, rootId)
        try {
            // step(), not resume(): nothing is ever released past the callee's first boundary, so a read that
            // succeeded here provably did not need a checkpoint or a wait to see the value.
            engine.step()
            engine.awaitQuiescent()

            assertEquals(BindingLookup.Present("handleA"), firstRead,
                "the caller's bootstrap value is in scope from the callee's very first instruction")
            val calleeNode = engine.snapshot().root.children.single()
            assertTrue(calleeNode.status is NodeStatus.Suspended,
                "the callee is still parked at its first boundary — nothing released it before that read")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun theSameCalleeRunsTwiceAgainstTwoDifferentBootstrapValues() = runBlocking {
        // The whole point of supplying the dependency at the call-site: ONE unparameterized callee, run against
        // two different subjects by the caller alone. If the callee had to name its own subject, a second one
        // would mean editing or duplicating it — the callee stays unaware that there is more than one.
        val reads = ArrayList<BindingLookup>()
        var firstOutput: Any? = null
        var secondOutput: Any? = null

        val callee = logicOf { execution ->
            val seen = execution.binding(ContextKey.of("sut"))
            reads.add(seen)
            TupleValue.ofMain(seen.valueOrNull())
        }
        val caller = logicOf { execution ->
            firstOutput = execution
                .host(
                    ObjectStableId("first"), callee,
                    initialBindings = listOf(InitialBinding(ContextKey.of("sut"), "handleA")))
                .mainComponentValue()
            secondOutput = execution
                .host(
                    ObjectStableId("second"), callee,
                    initialBindings = listOf(InitialBinding(ContextKey.of("sut"), "handleB")))
                .mainComponentValue()
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())

            assertEquals(
                listOf<BindingLookup>(BindingLookup.Present("handleA"), BindingLookup.Present("handleB")), reads,
                "the same callee saw a different subject per call — the value is per-invocation, not per-Logic")
            assertEquals("handleA", firstOutput, "each invocation returns what it was run against")
            assertEquals("handleB", secondOutput, "each invocation returns what it was run against")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aBootstrapBindingCarriesNoDisposalSoReleasingItInsideTheCalleeClosesNothing() = runBlocking {
        // A borrow is not a handover: the value's owner is whatever frame bound it on the caller's side, and
        // that frame outlives the sequential callee. So a release inside the callee unbinds the NAME and closes
        // nothing — otherwise supplying a subject would silently hand the callee a lifetime it never asked for,
        // and the caller's own resource would die halfway through its own frame.
        val disposed = ArrayList<String>()
        var disposedInsideCallee: List<String>? = null
        var readAfterReleasingABorrowOnlyName: BindingLookup? = null
        var readAfterReleasingTheBorrowedName: BindingLookup? = null
        var callerReadAfterCallee: BindingLookup? = null

        val callee = logicOf { execution ->
            execution.releaseBinding(ContextKey.of("aux"))
            execution.releaseBinding(ContextKey.of("sut"))
            disposedInsideCallee = disposed.toList()
            readAfterReleasingABorrowOnlyName = execution.binding(ContextKey.of("aux"))
            readAfterReleasingTheBorrowedName = execution.binding(ContextKey.of("sut"))
            TupleValue.ofMain("callee")
        }
        val caller = logicOf { execution ->
            execution.bind(
                ContextKey.of("sut"), "handle", FrameDisposal(ClosePolicy.Auto) { disposed.add("sut") })
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(
                    InitialBinding(ContextKey.of("sut"), "handle"),
                    InitialBinding(ContextKey.of("aux"), "aux-handle")))
            callerReadAfterCallee = execution.binding(ContextKey.of("sut"))
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())

            assertEquals(
                listOf<String>(), disposedInsideCallee,
                "releasing a borrow closes nothing — there is no disposal attached to claim")
            assertEquals(
                BindingLookup.Missing, readAfterReleasingABorrowOnlyName,
                "a borrow the caller holds no registration of simply stops being in scope")
            assertEquals(
                BindingLookup.Present("handle"), readAfterReleasingTheBorrowedName,
                "the release stopped at the callee's own frame, so the owner's registration shows through " +
                        "underneath rather than being reached across into")
            assertEquals(
                BindingLookup.Present("handle"), callerReadAfterCallee,
                "the owner's registration is untouched by anything the callee did to its borrow")
            assertEquals(
                listOf("sut"), disposed,
                "the owner disposes exactly once, at the settle of the frame that actually bound it")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aChildsOwnBindUnderTheBootstrapKeySupersedesTheBorrowOnItsOwnFrame() = runBlocking {
        // A borrow occupies an ordinary registry slot on the callee's OWN frame, so a callee that binds that
        // name is doing the ordinary same-frame supersede — substituting its own subject for the supplied one
        // without reaching up into the caller's registry. That containment is what keeps the loan scoped: the
        // callee can always override what it was handed, and the caller never observes it.
        var readBeforeOwnBind: BindingLookup? = null
        var readAfterOwnBind: BindingLookup? = null
        var callerReadAfterCallee: BindingLookup? = null

        val callee = logicOf { execution ->
            readBeforeOwnBind = execution.binding(ContextKey.of("sut"))
            execution.bind(ContextKey.of("sut"), "own")
            readAfterOwnBind = execution.binding(ContextKey.of("sut"))
            TupleValue.ofMain("callee")
        }
        val caller = logicOf { execution ->
            execution.bind(ContextKey.of("sut"), "callerOwned")
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(InitialBinding(ContextKey.of("sut"), "borrowed")))
            callerReadAfterCallee = execution.binding(ContextKey.of("sut"))
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())

            assertEquals(BindingLookup.Present("borrowed"), readBeforeOwnBind,
                "the borrow shadows the caller's own binding of the same name for the callee's duration")
            assertEquals(BindingLookup.Present("own"), readAfterOwnBind,
                "the callee's own bind wins on its own frame — a borrow is not privileged over it")
            assertEquals(BindingLookup.Present("callerOwned"), callerReadAfterCallee,
                "neither the borrow nor the callee's override of it is visible on the caller's side")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun anAdoptedLocalBindingSupersedesTheSameKeyBootstrapValueOnRebuild() = runBlocking {
        // The two sources of a name on a rebuilt child frame are NOT symmetric, and this is the fixture that
        // pins which wins: a bootstrap value is a re-supplied borrow of whatever the caller holds now, whereas
        // an adopted binding is one the child itself opened — migration-owned, keeping the stable owner it
        // bound to. Letting a stale re-read displace the child's own live handle would drop that handle
        // WITHOUT closing it (a borrow carries no disposal), so adoption has to land last.
        val sut = ContextKey.of("sut")
        val disposed = ArrayList<String>()
        val readsAtStart = ArrayList<BindingLookup>()
        var readAfterOwnBind: BindingLookup? = null
        var callerSupplies = "beforeEdit"

        val callee = logicOf { execution ->
            readsAtStart.add(execution.binding(sut))
            if (readsAtStart.size == 1) {
                // Opened once, then adopted rather than re-opened — the "own handle" shape a live edit must
                // not disturb.
                execution.bind(sut, "calleeOwned", FrameDisposal(ClosePolicy.Auto) { disposed.add("calleeOwned") })
                readAfterOwnBind = execution.binding(sut)
            }
            parkForever(execution)
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(InitialBinding(sut, callerSupplies)))
            parkForever(execution)
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertEquals(BindingLookup.Present("beforeEdit"), readsAtStart.single(),
                "the callee starts from what the caller supplied")
            assertEquals(BindingLookup.Present("calleeOwned"), readAfterOwnBind,
                "and then substitutes the handle it opened itself")

            // The edit changes what the caller would supply; the callee's stable id is unchanged, so the
            // rebuilt frame is the one that adopts.
            callerSupplies = "afterEdit"
            engine.migrate(caller, paused = true)
            engine.awaitQuiescent()

            assertEquals(2, readsAtStart.size, "the rebuilt callee re-ran from its first instruction")
            assertEquals(BindingLookup.Present("calleeOwned"), readsAtStart[1],
                "an adopted binding supersedes a same-key bootstrap value: the bootstrap is a borrow the " +
                        "rebuilt caller re-supplies, while the adopted one is the callee's own live handle, " +
                        "which keeps its owner across the edit")
            assertTrue(disposed.isEmpty(), "the adopted handle crossed the barrier open, not disposed")

            engine.cancel()
            engine.awaitQuiescent()
            assertEquals(listOf("calleeOwned"), disposed,
                "the adopted handle still disposes exactly once, at the settle of the frame that owns it")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aBootstrapValueIsReSuppliedFromTheCallersCurrentStateOnRebuild() = runBlocking {
        // The other half of the same rule, with nothing of the callee's own under the key: a borrow is read
        // from the caller's CURRENT state at every hosting, so an edit that changes what the caller supplies
        // reaches the rebuilt callee. Nothing owns the borrowed value on the callee's side for the barrier to
        // preserve — preserving it would pin the callee to a subject the edited caller no longer names.
        val sut = ContextKey.of("sut")
        val readsAtStart = ArrayList<BindingLookup>()
        var callerSupplies = "beforeEdit"

        val callee = logicOf { execution ->
            readsAtStart.add(execution.binding(sut))
            parkForever(execution)
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(InitialBinding(sut, callerSupplies)))
            parkForever(execution)
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.step()
            engine.awaitQuiescent()
            assertEquals(BindingLookup.Present("beforeEdit"), readsAtStart.single(),
                "the callee starts from what the caller supplied")

            callerSupplies = "afterEdit"
            engine.migrate(caller, paused = true)
            engine.awaitQuiescent()

            assertEquals(2, readsAtStart.size, "the rebuilt callee re-ran from its first instruction")
            assertEquals(BindingLookup.Present("afterEdit"), readsAtStart[1],
                "a borrowed value the callee never took ownership of is re-supplied from the edited caller, " +
                        "not carried over from the superseded generation — a disposal-less borrow must not be " +
                        "lifted at the barrier and re-adopted over what the rebuilt caller just supplied")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aDuplicateBootstrapKeyKeepsTheLastEntry() = runBlocking {
        // The supplied list lands in one registry slot per key, in order, so a caller that COMPOSES its
        // bindings (a base set with an override appended) gets last-wins — the same rule as re-binding a name.
        // Rejecting the duplicate would push de-duplication onto every caller that builds the list.
        var read: BindingLookup? = null

        val callee = logicOf { execution ->
            read = execution.binding(ContextKey.of("sut"))
            TupleValue.ofMain("callee")
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(
                    InitialBinding(ContextKey.of("sut"), "base"),
                    InitialBinding(ContextKey.of("sut"), "override")))
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            engine.resume()
            assertIs<Outcome.Success>(engine.await())
            assertEquals(BindingLookup.Present("override"), read,
                "a later entry overrides an earlier one under the same key")
        }
        finally {
            engine.close()
        }
    }
}
