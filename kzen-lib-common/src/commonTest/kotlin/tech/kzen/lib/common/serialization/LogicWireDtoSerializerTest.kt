package tech.kzen.lib.common.serialization

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunInfo
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.exec.task.model.TaskId
import tech.kzen.lib.common.exec.task.model.TaskModel
import tech.kzen.lib.common.exec.task.model.TaskState
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


// SER4: the run / task wire DTOs migrated from hand-written toCollection()/ofCollection() map codecs to
// generated kotlinx codecs. These pin the wire form (not just the round-trip): the "null"-string sentinel is
// gone (LogicStatus.active is a real JSON null), the unbounded Longs encode as strings, and the recursive
// LogicRunFrameInfo tree survives. This is the acceptance proof that replaces Ser4SpikeTest.kt (now deleted).
class LogicWireDtoSerializerTest {
    private inline fun <reified T> roundTrip(value: T) {
        val encoded = Json.encodeToString(value)
        val decoded = Json.decodeFromString<T>(encoded)
        assertEquals(value, decoded, "round-trip failed for <$value> (encoded=$encoded)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val rootLocation = ObjectLocation.parse("main/main.yaml#main")
    private val stepLocation = ObjectLocation.parse("main/sub.yaml#main.steps/Text")

    // A two-level frame tree (root -> one dependency) — exercises the recursive @Serializable on the real type.
    private val leafFrame = LogicRunFrameInfo(stepLocation, LogicExecutionId("exec-2"), listOf())
    private val rootFrame = LogicRunFrameInfo(
        rootLocation, LogicExecutionId("exec-1"), listOf(leafFrame), position = stepLocation)

    private val runInfo = LogicRunInfo(LogicRunId("run-1"), rootFrame, LogicRunState.Paused, 42L)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun logicStatusRoundTrip() {
        roundTrip(LogicStatus(7L, 3L, runInfo))
        roundTrip(LogicStatus(0L, 0L, null))
        // Large epoch/sequence beyond JS's 2^53 safe integer — the whole reason these ride LongAsString.
        roundTrip(LogicStatus(9_007_199_254_740_993L, 1L,
            runInfo.copy(sequence = 9_007_199_254_740_993L)))
    }


    @Test
    fun logicStatusKillsNullSentinel() {
        // LOAD-BEARING: `active` is nullable WITHOUT a default, so an absent run encodes as an EXPLICIT JSON null
        // — not the old literal "null" STRING, and not an omitted key. This is what the Ser4SpikeTest de-risked.
        val encoded = Json.encodeToJsonElement(LogicStatus(7L, 3L, null)).jsonObject
        assertEquals(JsonNull, encoded["active"], "active must be explicit JSON null, was ${encoded["active"]}")

        assertNull(Json.decodeFromString<LogicStatus>(
            """{"epoch":"7","structureVersion":"3","active":null}""").active)
    }


    @Test
    fun logicStatusEncodesLongsAsStrings() {
        val encoded = Json.encodeToJsonElement(LogicStatus(7L, 3L, runInfo)).jsonObject
        assertTrue(encoded["epoch"]!!.jsonPrimitive.isString, "epoch must be a string Long")
        assertEquals("7", encoded["epoch"]!!.jsonPrimitive.content)
        assertTrue(encoded["structureVersion"]!!.jsonPrimitive.isString, "structureVersion must be a string Long")

        val activeObject = encoded["active"]!!.jsonObject
        assertTrue(activeObject["sequence"]!!.jsonPrimitive.isString, "sequence must be a string Long")
        assertEquals("42", activeObject["sequence"]!!.jsonPrimitive.content)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun logicRunFrameInfoRecursiveRoundTrip() {
        roundTrip(rootFrame)
        // A node with no position and no dependencies — the leaf case.
        roundTrip(LogicRunFrameInfo(stepLocation, LogicExecutionId("solo"), listOf()))
    }


    @Test
    fun logicRunFrameInfoSerialNamesAndOmittedPosition() {
        val leaf = Json.encodeToJsonElement(leafFrame).jsonObject
        // @SerialName preserves the short wire keys; the property names must NOT leak onto the wire.
        assertTrue("location" in leaf && "execution" in leaf, "expected short keys, got ${leaf.keys}")
        assertTrue("objectLocation" !in leaf && "executionId" !in leaf)
        // position = null (its default) is OMITTED, matching the old buildMap.
        assertTrue("position" !in leaf, "null position must be omitted, got ${leaf.keys}")
        assertEquals(stepLocation.asString(), leaf["location"]!!.jsonPrimitive.content)

        // ...and present when set.
        assertTrue("position" in Json.encodeToJsonElement(rootFrame).jsonObject)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val request = ExecutionRequest(RequestParams.of("a" to "1"), null)
    private val emptyRequest = ExecutionRequest(RequestParams.empty, null)
    private val success = ExecutionSuccess.ofValue(ExecutionValue.of("hello"))
    private val failure = ExecutionFailure("boom")


    @Test
    fun taskModelRoundTrip() {
        // Running with a partial success, no final result.
        roundTrip(TaskModel(TaskId("task-1"), rootLocation, request, TaskState.Running, success, null))
        // The parameterless-submit shape, which is what every Custom task card actually sends and polls back.
        // `request` above carries params, and that non-empty fixture was the reason RequestParams.parse("")
        // stayed broken behind a green round-trip test.
        roundTrip(TaskModel(TaskId("task-1"), rootLocation, emptyRequest, TaskState.Running, success, null))
        // Finished with a failure, no partial.
        roundTrip(TaskModel(TaskId("task-1"), rootLocation, request, TaskState.FinishedOrFailed, null, failure))
        // Finished with a success final result.
        roundTrip(TaskModel(TaskId("task-1"), rootLocation, request, TaskState.FinishedOrFailed, success, success))
    }


    @Test
    fun taskModelSerialNames() {
        val encoded = Json.encodeToJsonElement(
            TaskModel(TaskId("task-1"), rootLocation, request, TaskState.Running, success, null)).jsonObject

        for (expectedKey in listOf("id", "location", "request", "state", "partial", "result")) {
            assertTrue(expectedKey in encoded, "missing wire key '$expectedKey' in ${encoded.keys}")
        }
        assertTrue("taskId" !in encoded && "partialResult" !in encoded && "finalResult" !in encoded)
        // TaskId encodes as the bare string identifier.
        assertEquals("task-1", encoded["id"]!!.jsonPrimitive.content)
        // A null finalResult (no default) is an explicit JSON null, not omitted.
        assertEquals(JsonNull, encoded["result"])
    }
}
