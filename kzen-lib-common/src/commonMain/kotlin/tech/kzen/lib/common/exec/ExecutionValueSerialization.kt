package tech.kzen.lib.common.exec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull


//---------------------------------------------------------------------------------------------------------------------
// SER2: kotlinx serializers for the ExecutionValue / ExecutionResult / ExecutionRequest wire envelopes.
//
// Design (SER plan 2d): these wrap the EXISTING toJsonCollection()/fromJsonCollection() lowering rather than
// re-encoding onto JsonElement natively, so the `{type, value}` envelope (base64 binary, string-encoded Long,
// the `json` primitive-subtree fast-path, and TP3's flat `binary-handle` variant) is preserved byte-for-byte.
// ExecutionValue stays a Digestible runtime tree; this only wraps its wire encoding. The ExecutionValue.kt:11
// TODO ("use JsonElement natively?") is thereby DECLINED — toJsonCollection remains the single source of truth.
//
// These are JSON-only serializers (they require a JsonEncoder/JsonDecoder); the whole structured wire is JSON.


//--- Any <-> JsonElement bridge (the lowered map form uses raw Kotlin values) --------------------------------------

private fun anyToJsonElement(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)

        // NB: `is Long` must stay ABOVE `is Number` (Long is a Number), and is the one numeric type check that
        // means the same thing on both platforms — Long is a real class on JS, not a JS `number`.
        is Long -> JsonPrimitive(value)

        // Dispatch on the VALUE, never on the static number type: on Kotlin/JS every Double IS a JS `number`, so
        // `is Int` matches 3.14, NaN and Infinity alike, and an `is Int` branch placed above `is Double` makes the
        // Double branch unreachable there. That is exactly how NaN used to escape the non-finite check below and
        // reach JsonPrimitive(Int) -> a bare `NaN` token -> JsonEncodingException, on JS only. `is Number` +
        // toDouble() is platform-independent, and mirrors ExecutionValue.ofArbitrary's long-standing shape.
        is Number -> {
            val asDouble = value.toDouble()
            if (asDouble.isFinite()) {
                JsonPrimitive(value)
            }
            else {
                // Non-finite (Infinity/NaN) can't be a JSON number; fromJsonCollection's number branch already
                // accepts a String ("// NB: handle Infinity"). Emitting a string keeps the wire valid JSON.
                JsonPrimitive(asDouble.toString())
            }
        }
        is Map<*, *> ->
            JsonObject(value.entries.associate { (k, v) -> (k as String) to anyToJsonElement(v) })
        is List<*> ->
            JsonArray(value.map { anyToJsonElement(it) })
        else ->
            error("Unsupported value for JSON encoding: $value (${value::class})")
    }


private fun jsonElementToAny(element: JsonElement): Any? =
    when (element) {
        is JsonNull ->
            null

        is JsonObject ->
            element.mapValues { jsonElementToAny(it.value) }

        is JsonArray ->
            element.map { jsonElementToAny(it) }

        is JsonPrimitive ->
            when {
                element.isString ->
                    element.content

                else -> {
                    val asBoolean = element.booleanOrNull
                    if (asBoolean != null) {
                        asBoolean
                    }
                    else {
                        // All JSON numbers decode to Double: fromJsonCollection's `number` branch and the json
                        // fast-path (fromJsonPrimitiveCollection) accept Double (or String), never Int/Long; the
                        // binary-handle `size` reads via `as Number`, so Double is safe there too.
                        element.content.toDouble()
                    }
                }
            }
    }


//--- Serializers ---------------------------------------------------------------------------------------------------

object ExecutionValueSerializer: KSerializer<ExecutionValue> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.kzen.lib.common.exec.ExecutionValue")

    override fun serialize(encoder: Encoder, value: ExecutionValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ExecutionValue requires a JSON encoder")
        jsonEncoder.encodeJsonElement(anyToJsonElement(value.toJsonCollection()))
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): ExecutionValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ExecutionValue requires a JSON decoder")
        val element = jsonDecoder.decodeJsonElement()
        return ExecutionValue.fromJsonCollection(jsonElementToAny(element) as Map<String, Any>)
    }
}


object ExecutionResultSerializer: KSerializer<ExecutionResult> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.kzen.lib.common.exec.ExecutionResult")

    override fun serialize(encoder: Encoder, value: ExecutionResult) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ExecutionResult requires a JSON encoder")
        // toJsonCollection already recurses into the nested ExecutionValue trees, so the full envelope lowers here.
        jsonEncoder.encodeJsonElement(anyToJsonElement(value.toJsonCollection()))
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): ExecutionResult {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ExecutionResult requires a JSON decoder")
        val element = jsonDecoder.decodeJsonElement()
        return ExecutionResult.fromJsonCollection(jsonElementToAny(element) as Map<String, Any?>)
    }
}


object ExecutionRequestSerializer: KSerializer<ExecutionRequest> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("tech.kzen.lib.common.exec.ExecutionRequest")

    override fun serialize(encoder: Encoder, value: ExecutionRequest) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ExecutionRequest requires a JSON encoder")
        jsonEncoder.encodeJsonElement(anyToJsonElement(value.toJsonCollection()))
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): ExecutionRequest {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ExecutionRequest requires a JSON decoder")
        val element = jsonDecoder.decodeJsonElement()
        return ExecutionRequest.fromJsonCollection(jsonElementToAny(element) as Map<String, String?>)
    }
}
