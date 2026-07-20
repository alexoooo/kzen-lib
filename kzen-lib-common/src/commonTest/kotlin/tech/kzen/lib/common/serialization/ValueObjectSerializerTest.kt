package tech.kzen.lib.common.serialization

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals


// SER2 (2c): each value-object KSerializer delegates to the class's asString()/parse() (or .value), round-tripping
// through the bound @Serializable(with=...).
class ValueObjectSerializerTest {
    private inline fun <reified T> roundTrip(value: T) {
        val encoded = Json.encodeToString(value)
        val decoded = Json.decodeFromString<T>(encoded)
        assertEquals(value, decoded, "round-trip failed for <$value> (encoded=$encoded)")
    }


    @Test
    fun documentPath() {
        roundTrip(DocumentPath.parse("foo/bar/~main.yaml"))
        roundTrip(DocumentPath.parse("hello world/aggregate test.yaml"))
    }


    @Test
    fun objectLocation() {
        roundTrip(ObjectLocation.parse("main/main.yaml#main.steps/If.then/Text"))
        roundTrip(ObjectLocation.parse("main/main.yaml#/foo"))
    }


    @Test
    fun objectPath() {
        roundTrip(ObjectLocation.parse("main/main.yaml#main.steps/If.then/Text").objectPath)
    }


    @Test
    fun attributePath() {
        roundTrip(AttributePath.parse("input"))
        roundTrip(AttributePath.parse("name.sub"))
    }


    @Test
    fun attributeNameEscapesDelimiter() {
        roundTrip(AttributeName("plain"))

        val withDot = AttributeName("with.dot")
        // The wire form is the delimiter-escaped asString() ("with\.dot"), NOT the raw value — this is the gotcha
        // the serializer must honour (serialize via asString()/parse(), never the field).
        val wireContent = Json.decodeFromString<String>(Json.encodeToString(withDot))
        assertEquals(withDot.asString(), wireContent)
        assertEquals("with\\.dot", wireContent)
        roundTrip(withDot)
    }


    @Test
    fun attributeNesting() {
        roundTrip(AttributeNesting.empty)
        roundTrip(AttributePath.parse("input.a.b").nesting)
    }


    @Test
    fun digest() {
        roundTrip(Digest.zero)
        roundTrip(Digest.empty)
        roundTrip(Digest.missing)
        roundTrip(Digest(1, 2, 3, 4))
        roundTrip(Digest(-5, 100000, -99999, 12345))
    }


    @Test
    fun logicIds() {
        roundTrip(LogicRunId("run-abc"))
        roundTrip(LogicExecutionId("2026-07-16T00:00:00Z"))
        roundTrip(LogicExecutionId("exec_123"))
    }


    @Test
    fun requestParams() {
        // Empty IS the wire reality, not an edge case: every parameterless task submit sends
        // ExecutionRequest(RequestParams.empty, null), and the server echoes `"params":""` back in each TaskModel.
        roundTrip(RequestParams.empty)
        roundTrip(RequestParams.of("a" to "1"))
        roundTrip(RequestParams(mapOf("k" to listOf("v1", "v2"))))
    }
}
