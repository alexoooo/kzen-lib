package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.ContextFamily
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.context.ExportSelector
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Binding semantics when sibling frames run **concurrently** — the shape a Job takes, where the Job document is
 * one frame and every Worker is a child frame launched together (`JobRun`), as opposed to the sequential host
 * chain a Script builds.
 *
 * The first three are **characterization** fixtures written for the CX8 reach gate, not a blessing of the
 * behaviour they record. The engine's binding model is specified for a sequential host chain throughout —
 * logic-spec.md §6 argues supersession from "a loop re-providing a browser once per iteration" and argues a
 * borrow's safety from a caller frame that "outlives the sequential child by construction". The spec says
 * nothing at all about two frames doing this at once. What that silence actually resolves to today is pinned
 * here, so that any later change to it is a deliberate decision rather than an accident, and so the gate's
 * verdict rests on measured behaviour rather than on a reading of the engine. **Two of the three are recorded
 * hazards** and are labelled ⚠ at their site; both are open as ledger row 43.
 *
 * The last two are ordinary **regression** fixtures for the one thing the gate found that was outright broken
 * rather than merely unspecified — a bind travelling the export chain leaving the borrow it replaced behind to
 * shadow it (ledger row 42, fixed).
 */
class RunEngineParallelBindingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootId = ObjectStableId("root")
    private val sut = ContextKey.of("sut")


    private fun logicOf(block: suspend (Execution) -> TupleValue): Logic =
        object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution) = block(execution)
        }


    /** Host [a] and [b] as concurrent children of the calling frame — the `JobRun` worker-launch shape. */
    private suspend fun hostBoth(execution: Execution, a: Logic, b: Logic) {
        coroutineScope {
            listOf(
                async { execution.host(ObjectStableId("a"), a) },
                async { execution.host(ObjectStableId("b"), b) }
            ).awaitAll()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun concurrentSiblingsThatExportNothingKeepTheirBindingsPrivateToTheirOwnFrames() = runBlocking {
        // The baseline, and the good news for the gate: with no export declared anywhere, `exportOwnerOf` returns
        // the binding frame itself, so two workers binding the SAME key at the SAME time never meet. Each rests on
        // its own frame, each shadows nothing, and each disposes at its own settle. This is what a Job worker gets
        // today by construction — the Job frame declares no exports, so it ends every export chain that reaches it.
        val disposed = CopyOnWriteArrayList<String>()
        val bothBound = CompletableDeferred<Unit>()
        var readInA: BindingLookup? = null
        var readInB: BindingLookup? = null
        var readInParent: BindingLookup? = null

        fun worker(name: String, other: CompletableDeferred<Unit>, own: CompletableDeferred<Unit>) =
            logicOf { execution ->
                execution.bind(sut, name, FrameDisposal(ClosePolicy.Auto) { disposed.add(name) })
                own.complete(Unit)
                // Both bindings are live at once when the reads below happen — the point of the fixture.
                other.await()
                if (name == "a") { readInA = execution.binding(sut) } else { readInB = execution.binding(sut) }
                bothBound.complete(Unit)
                TupleValue.ofMain(name)
            }

        val aBound = CompletableDeferred<Unit>()
        val bBound = CompletableDeferred<Unit>()
        val parent = logicOf { execution ->
            hostBoth(execution, worker("a", bBound, aBound), worker("b", aBound, bBound))
            readInParent = execution.binding(sut)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            withTimeout(30_000) {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals(BindingLookup.Present("a"), readInA,
                "each frame reads its own binding while the sibling's is equally live — a per-frame registry, " +
                        "and a lookup walk that only ever climbs, never descends into a sibling")
            assertEquals(BindingLookup.Present("b"), readInB,
                "and symmetrically for the sibling, under the very same key")
            assertEquals(BindingLookup.Missing, readInParent,
                "neither reached the shared parent: an unexported bind is private to the frame that made it, " +
                        "so concurrency adds no exposure of its own")
            assertEquals(setOf("a", "b"), disposed.toSet(),
                "and each disposes exactly once, at the settle of the frame that bound it")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun twoConcurrentSiblingsExportingOneKeyCollideAndTheLosersLiveHandleIsClosedUnderneathIt() = runBlocking {
        // ⚠ RECORDED HAZARD — the central finding of the CX8 gate.
        //
        // Add one export declaration to the fixture above and the isolation is gone. Both siblings' bindings now
        // resolve to the SAME slot on the shared parent, so the second bind displaces the first, claims its
        // disposal, and runs the closer — while the first sibling is still running and still driving what that
        // closer just closed. The first sibling's next read then returns its sibling's handle.
        //
        // The engine is not doing anything wrong at its own level: the claim is under the lock, so exactly one
        // caller wins and nothing leaks or double-closes. But `bind`'s supersede rule was reasoned for a loop
        // re-binding sequentially (RunEngine.kt:1303-1309), where the displaced handle is provably nobody's
        // business by then. Under concurrency the displaced handle is a live sibling's, the winner is whichever
        // frame happened to be scheduled second, and nothing anywhere reports it.
        val disposed = CopyOnWriteArrayList<String>()
        val aBound = CompletableDeferred<Unit>()
        val bBound = CompletableDeferred<Unit>()
        var readInAAfterSiblingBound: BindingLookup? = null
        var disposedWhenAResumed: List<String>? = null
        var readInParent: BindingLookup? = null

        val childA = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("sut")))
            execution.bind(sut, "browserA", FrameDisposal(ClosePolicy.Auto) { disposed.add("browserA") })
            aBound.complete(Unit)
            bBound.await()
            disposedWhenAResumed = disposed.toList()
            readInAAfterSiblingBound = execution.binding(sut)
            TupleValue.ofMain("a")
        }
        val childB = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("sut")))
            aBound.await()
            execution.bind(sut, "browserB", FrameDisposal(ClosePolicy.Auto) { disposed.add("browserB") })
            bBound.complete(Unit)
            TupleValue.ofMain("b")
        }
        val parent = logicOf { execution ->
            hostBoth(execution, childA, childB)
            readInParent = execution.binding(sut)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            withTimeout(30_000) {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals(listOf("browserA"), disposedWhenAResumed,
                "the loser's closer already ran — its browser was closed while the frame that opened it was " +
                        "still running and had not finished with it")
            assertEquals(BindingLookup.Present("browserB"), readInAAfterSiblingBound,
                "and the loser now reads its SIBLING's handle under its own name, silently: last writer wins, " +
                        "and which sibling that is comes down to scheduling")
            assertEquals(BindingLookup.Present("browserB"), readInParent,
                "one slot on the shared parent, so the export collapsed two resources into one")
            assertEquals(listOf("browserA", "browserB"), disposed,
                "the survivor disposes at the parent's settle, so nothing leaks and nothing double-closes — " +
                        "the failure is semantic, not a resource-safety failure")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun oneConcurrentSiblingsReleaseUnbindsAndDisposesASharedAncestorsBindingUnderTheOther() = runBlocking {
        // ⚠ RECORDED HAZARD — the release half of the same problem.
        //
        // `releaseBinding` removes the NEAREST binding on the ancestor chain (RunEngine.kt:1342-1350), which is
        // deliberate: a sibling closing step is meant to be able to release what an earlier sibling opened onto
        // their shared parent. Sequentially that is exactly right. Concurrently it means any one frame can close
        // a shared resource out from under every other frame that is using it, with no coordination and no
        // signal — the release does not consult, and could not consult, who else is reading.
        val disposed = CopyOnWriteArrayList<String>()
        val aReady = CompletableDeferred<Unit>()
        val bReleased = CompletableDeferred<Unit>()
        var readInABefore: BindingLookup? = null
        var readInAAfter: BindingLookup? = null
        var disposedWhenAResumed: List<String>? = null
        var readInParent: BindingLookup? = null

        val childA = logicOf { execution ->
            readInABefore = execution.binding(sut)
            aReady.complete(Unit)
            bReleased.await()
            disposedWhenAResumed = disposed.toList()
            readInAAfter = execution.binding(sut)
            TupleValue.ofMain("a")
        }
        val childB = logicOf { execution ->
            aReady.await()
            execution.releaseBinding(sut)
            bReleased.complete(Unit)
            TupleValue.ofMain("b")
        }
        val parent = logicOf { execution ->
            execution.bind(sut, "shared", FrameDisposal(ClosePolicy.Auto) { disposed.add("shared") })
            hostBoth(execution, childA, childB)
            readInParent = execution.binding(sut)
            TupleValue.ofMain("parent")
        }

        val engine = RunEngine(parent, rootId)
        try {
            withTimeout(30_000) {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals(BindingLookup.Present("shared"), readInABefore,
                "both siblings inherit the parent's binding — that inheritance is the whole point of ambient")
            assertEquals(listOf("shared"), disposedWhenAResumed,
                "the sibling's release reached UP into the parent's registry and ran the closer, mid-run, " +
                        "while the other sibling was still holding what it closed")
            assertEquals(BindingLookup.Missing, readInAAfter,
                "and the other sibling's next read simply reports Missing, with nothing to distinguish " +
                        "'never bound' from 'closed underneath me by a peer'")
            assertEquals(BindingLookup.Missing, readInParent,
                "the owner itself is left without the binding it opened")
            assertEquals(listOf("shared"), disposed,
                "disposed exactly once, by the release rather than by the settle")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aCalleeExportingItsBootstrapKeyReadsTheValueItBoundRatherThanTheBorrowItReplaced() = runBlocking {
        // The regression fixture for the defect the CX8 gate found (ledger row 42), which was reachable in
        // shipped code: a Script `RunStep.contexts` call whose callee also declares `context.exports` covering
        // the same Context.
        //
        // The two mechanisms disagreed about which frame a key belongs to. `host` installs a bootstrap binding
        // by direct map write onto the CALLEE's own frame — correct, since the callee cannot have declared its
        // exports yet. But a later `bind` routes through `exportOwnerOf`, which climbs PAST that frame once the
        // callee has declared the export, landing the value on the caller while the borrow stayed below,
        // shadowing it: the callee could not see the value it had just bound, for the rest of its run. Not a
        // leak — the bind reached its owner and disposed correctly — but every callee-side read was wrong.
        //
        // `bind` now supersedes the borrows the climb travels past, so both halves hold at once: the export
        // still moves ownership up, and the binder still reads what it bound. Note how narrowly the old
        // coverage missed it — `aChildsOwnBindUnderTheBootstrapKeySupersedesTheBorrowOnItsOwnFrame`
        // (RunEngineTest) pins the same rule for the NON-exporting callee, where `exportOwnerOf` returns the
        // callee itself and the in-place overwrite does the superseding for free. One `declareExport` away.
        val disposed = CopyOnWriteArrayList<String>()
        var readInCalleeBeforeOwnBind: BindingLookup? = null
        var readInCalleeAfterOwnBind: BindingLookup? = null
        var readInCallerAfterCallee: BindingLookup? = null
        var disposedWhenCallerResumed: List<String>? = null

        val callee = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("sut")))
            readInCalleeBeforeOwnBind = execution.binding(sut)
            execution.bind(sut, "own", FrameDisposal(ClosePolicy.Auto) { disposed.add("own") })
            readInCalleeAfterOwnBind = execution.binding(sut)
            TupleValue.ofMain("callee")
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("callee"), callee,
                initialBindings = listOf(InitialBinding(sut, "borrowed")))
            disposedWhenCallerResumed = disposed.toList()
            readInCallerAfterCallee = execution.binding(sut)
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            withTimeout(30_000) {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals(BindingLookup.Present("borrowed"), readInCalleeBeforeOwnBind,
                "the call site's borrow is what the callee starts from")
            assertEquals(BindingLookup.Present("own"), readInCalleeAfterOwnBind,
                "the callee reads what it bound: its own bind travelled up the export chain, and the borrow " +
                        "it replaced was superseded on the way rather than left behind to shadow it")
            assertEquals(BindingLookup.Present("own"), readInCallerAfterCallee,
                "and the bind still reached its intended owner — superseding the borrow does not cost the " +
                        "export its meaning, which is the whole point of fixing it this way")
            assertTrue(disposedWhenCallerResumed!!.isEmpty(),
                "and it is genuinely owned up there: the callee's settle did not dispose it")
            assertEquals(listOf("own"), disposed,
                "it disposes once, at the settle of the frame the export chain gave it to")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun aBindTravellingTheExportChainSupersedesEveryBorrowItTravelsPastNotOnlyItsOwn() = runBlocking {
        // Why the supersede walks the whole climbed path instead of clearing the binding frame alone: the borrow
        // that shadows a bind need not be on the frame that made it. An INTERMEDIATE frame on the export chain
        // can hold one, and a borrow shadows every read from its entire subtree — including the binder's, one
        // level below it. Same defect, one frame further away, and it would survive a fix that only looked at
        // self.
        //
        //   caller ──hosts──▶ middle (borrowed "sut", exports sut) ──hosts──▶ inner (exports sut, binds sut)
        //
        // The climb runs inner → middle → caller and stops there, caller being the first frame declaring no
        // export covering the key; so the value rests on caller and both frames below must resolve to it.
        val disposed = CopyOnWriteArrayList<String>()
        var readInInnerAfterOwnBind: BindingLookup? = null
        var readInMiddleAfterInner: BindingLookup? = null
        var readInCallerAfterMiddle: BindingLookup? = null

        val inner = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("sut")))
            execution.bind(sut, "own", FrameDisposal(ClosePolicy.Auto) { disposed.add("own") })
            readInInnerAfterOwnBind = execution.binding(sut)
            TupleValue.ofMain("inner")
        }
        val middle = logicOf { execution ->
            execution.declareExport(ExportSelector.Family(ContextFamily("sut")))
            execution.host(ObjectStableId("inner"), inner)
            readInMiddleAfterInner = execution.binding(sut)
            TupleValue.ofMain("middle")
        }
        val caller = logicOf { execution ->
            execution.host(
                ObjectStableId("middle"), middle,
                initialBindings = listOf(InitialBinding(sut, "borrowed")))
            readInCallerAfterMiddle = execution.binding(sut)
            TupleValue.ofMain("caller")
        }

        val engine = RunEngine(caller, rootId)
        try {
            withTimeout(30_000) {
                engine.resume()
                assertIs<Outcome.Success>(engine.await())
            }

            assertEquals(BindingLookup.Present("own"), readInInnerAfterOwnBind,
                "the binder reads what it bound, even though the borrow that would have shadowed it sat on a " +
                        "frame the binder does not own and never touched directly")
            assertEquals(BindingLookup.Present("own"), readInMiddleAfterInner,
                "and the frame whose borrow was superseded resolves to the current owner's value rather than " +
                        "to its own stale loan — the coherent reading, since ownership has moved above it")
            assertEquals(BindingLookup.Present("own"), readInCallerAfterMiddle,
                "two export declarations carried it two hops, exactly as an unbroken chain should")
            assertEquals(listOf("own"), disposed,
                "disposed once, by the frame the chain came to rest on")
        }
        finally {
            engine.close()
        }
    }
}
