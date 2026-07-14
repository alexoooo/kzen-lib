package tech.kzen.lib.server.exec.logic.trace

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The values-only reset surface ([LogicTraceStore.clearValues]) backing per-iteration trace resets
 * (logic-spec §7 resettable live state): live per-path values clear, the append-only events (film-strip)
 * and the most-recent index survive.
 */
class LogicTraceStoreResetTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mapper = ObjectStableMapper()
    private val store = LogicTraceStore(mapper)

    private val runId = LogicRunId("run-1")
    private val rootExecution = LogicRunExecutionId(runId, LogicExecutionId("n0"))
    private val childExecution = LogicRunExecutionId(runId, LogicExecutionId("n1"))
    private val grandchildExecution = LogicRunExecutionId(runId, LogicExecutionId("n2"))

    private val rootLocation = location("main.yaml", "main")
    private val childLocation = location("child.yaml", "main")
    private val grandchildLocation = location("grandchild.yaml", "main")
    private val runStepLocation = location("main.yaml", "Run")
    private val innerStepLocation = location("child.yaml", "Inner")
    private val stepA = location("main.yaml", "A")
    private val stepB = location("main.yaml", "B")


    private fun location(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }


    private fun path(objectLocation: ObjectLocation): LogicTracePath {
        return LogicTracePath.ofObjectStableId(mapper.objectStableId(objectLocation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun clearValuesRemovesPathsKeepsEvents() {
        val handle = store.handle(rootExecution, rootLocation, null, null)
        handle.set(path(stepA), ExecutionValue.of("a-done"))
        handle.set(path(stepB), ExecutionValue.of("b-done"))
        handle.append(mapper.objectStableId(stepA), ExecutionValue.of("screenshot"))

        store.clearValues(rootExecution, listOf(path(stepA)))

        val snapshot = store.lookup(rootExecution, LogicTraceQuery(LogicTracePath.root))!!
        assertNull(snapshot.values[path(stepA)], "cleared path removed from the live view")
        assertEquals(
            ExecutionValue.of("b-done"), snapshot.values[path(stepB)]?.value,
            "other paths untouched")
        assertEquals(
            1, store.lookupRunHistory(runId, 0).size,
            "the append-only film-strip survives the reset")
        assertEquals(
            rootExecution, store.mostRecent(rootLocation),
            "the most-recent index is untouched")
    }


    @Test
    fun clearValuesByCallSiteIsTransitiveWithinRun() {
        val rootHandle = store.handle(rootExecution, rootLocation, null, null)
        rootHandle.set(path(stepA), ExecutionValue.of("parent-value"))

        val childHandle = store.handle(
            childExecution, childLocation, rootExecution.logicExecutionId, runStepLocation)
        childHandle.set(path(innerStepLocation), ExecutionValue.of("child-value"))
        childHandle.append(mapper.objectStableId(innerStepLocation), ExecutionValue.of("child-screenshot"))

        val grandchildHandle = store.handle(
            grandchildExecution, grandchildLocation, childExecution.logicExecutionId, innerStepLocation)
        grandchildHandle.set(path(stepB), ExecutionValue.of("grandchild-value"))

        val otherRunId = LogicRunId("run-2")
        val otherExecution = LogicRunExecutionId(otherRunId, LogicExecutionId("n1"))
        val otherHandle = store.handle(
            otherExecution, location("other.yaml", "main"), null, runStepLocation)
        otherHandle.set(path(stepB), ExecutionValue.of("other-run-value"))

        store.clearValues(runId, listOf(mapper.objectStableId(runStepLocation)))

        val query = LogicTraceQuery(LogicTracePath.root)
        assertTrue(
            store.lookup(childExecution, query)!!.values.isEmpty(),
            "the call-site's invocation reads as not-run")
        assertTrue(
            store.lookup(grandchildExecution, query)!!.values.isEmpty(),
            "cleared transitively through the invocation's own hosted descendants")
        assertEquals(
            ExecutionValue.of("parent-value"),
            store.lookup(rootExecution, query)!!.values[path(stepA)]?.value,
            "the run's root buffer is untouched")
        assertEquals(
            ExecutionValue.of("other-run-value"),
            store.lookup(otherExecution, query)!!.values[path(stepB)]?.value,
            "a different run's buffer is untouched even from the same call-site")
        assertEquals(
            1, store.lookupRunHistory(runId, 0).size,
            "the cleared invocation's film-strip survives")
    }
}
