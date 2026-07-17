package tech.kzen.lib.common.exec

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import tech.kzen.lib.common.util.ImmutableByteArray
import kotlin.test.Test
import kotlin.test.assertEquals


// SER2 (2d): the ExecutionValue / ExecutionResult / ExecutionRequest KSerializers wrap the existing
// toJsonCollection()/fromJsonCollection() lowering, so the {type, value} envelope is preserved byte-for-byte
// (base64 binary, string-encoded Long, the `json` primitive-subtree fast-path, and TP3's flat `binary-handle`).
class ExecutionValueSerializerTest {
    private val allVariants: List<ExecutionValue> = listOf(
        NullExecutionValue,
        TextExecutionValue(""),
        TextExecutionValue("hello"),
        BooleanExecutionValue(true),
        BooleanExecutionValue(false),
        NumberExecutionValue(0.0),
        NumberExecutionValue(3.14),
        NumberExecutionValue(-1.0),
        NumberExecutionValue(Double.NaN),
        NumberExecutionValue(Double.POSITIVE_INFINITY),
        NumberExecutionValue(Double.NEGATIVE_INFINITY),
        LongExecutionValue(9007199254740993L),   // beyond JS safe int -> string-encoded
        LongExecutionValue(-42L),
        BinaryExecutionValue(ByteArray(0)),
        BinaryExecutionValue(byteArrayOf(1, 2, 3, 4, -5)),
        BinaryHandleExecutionValue("run-1", "1a2b_-3c4d_5e6f_-7g8h", 42, "image/png"),
        ListExecutionValue(listOf(TextExecutionValue("a"), NumberExecutionValue(1.0))),                 // json fast-path
        ListExecutionValue(listOf(NullExecutionValue, TextExecutionValue("a"))),                        // recursive (null leaf)
        MapExecutionValue(mapOf("x" to TextExecutionValue("y"), "n" to NumberExecutionValue(2.0))),     // json fast-path
        MapExecutionValue(mapOf("bin" to BinaryExecutionValue(byteArrayOf(9)), "t" to TextExecutionValue("z"))), // recursive
        MapExecutionValue(mapOf(
            "list" to ListExecutionValue(listOf(
                MapExecutionValue(mapOf("k" to BooleanExecutionValue(true))),
                LongExecutionValue(5L))),
            "handle" to BinaryHandleExecutionValue("r", "h", 7, "image/png")))                           // deep mix
    )


    @Test
    fun everyVariantRoundTrips() {
        for (variant in allVariants) {
            val encoded = Json.encodeToString(variant)
            val decoded = Json.decodeFromString<ExecutionValue>(encoded)
            assertEquals(variant, decoded, "round-trip failed (encoded=$encoded)")
        }
    }


    @Test
    fun longEncodesAsString() {
        assertEquals(
            buildJsonObject { put("type", "long"); put("value", "5") },
            Json.encodeToJsonElement<ExecutionValue>(LongExecutionValue(5L)))
    }


    @Test
    fun nonFiniteNumberEncodesAsString() {
        assertEquals(
            buildJsonObject { put("type", "number"); put("value", "NaN") },
            Json.encodeToJsonElement<ExecutionValue>(NumberExecutionValue(Double.NaN)))

        assertEquals(
            buildJsonObject { put("type", "number"); put("value", "Infinity") },
            Json.encodeToJsonElement<ExecutionValue>(NumberExecutionValue(Double.POSITIVE_INFINITY)))
    }


    @Test
    fun binaryHandleEncodesFlat() {
        assertEquals(
            buildJsonObject {
                put("type", "binary-handle")
                put("run", "run-1")
                put("hash", "abc")
                put("size", 42)
                put("mime", "image/png")
            },
            Json.encodeToJsonElement<ExecutionValue>(
                BinaryHandleExecutionValue("run-1", "abc", 42, "image/png")))
    }


    @Test
    fun executionResultRoundTrips() {
        val success: ExecutionResult = ExecutionResult.success(TextExecutionValue("v"), NumberExecutionValue(1.0))
        assertEquals(success, Json.decodeFromString<ExecutionResult>(Json.encodeToString(success)))

        val successHandle: ExecutionResult = ExecutionResult.success(
            BinaryHandleExecutionValue("r", "h", 3, "image/png"), NullExecutionValue)
        assertEquals(successHandle, Json.decodeFromString<ExecutionResult>(Json.encodeToString(successHandle)))

        val failure: ExecutionResult = ExecutionFailure("boom")
        assertEquals(failure, Json.decodeFromString<ExecutionResult>(Json.encodeToString(failure)))
    }


    @Test
    fun executionRequestRoundTrips() {
        val noBody = ExecutionRequest(RequestParams.of("a" to "1"), null)
        val decodedNoBody = Json.decodeFromString<ExecutionRequest>(Json.encodeToString(noBody))
        assertEquals(noBody.parameters, decodedNoBody.parameters)
        assertEquals(null, decodedNoBody.body)

        val withBody = ExecutionRequest(
            RequestParams.of("a" to "1", "b" to "2"),
            ImmutableByteArray.wrap(byteArrayOf(1, 2, 3, -4)))
        val decodedWithBody = Json.decodeFromString<ExecutionRequest>(Json.encodeToString(withBody))
        assertEquals(withBody.parameters, decodedWithBody.parameters)
        assertEquals(
            withBody.body?.toByteArray()?.toList(),
            decodedWithBody.body?.toByteArray()?.toList())
    }
}
