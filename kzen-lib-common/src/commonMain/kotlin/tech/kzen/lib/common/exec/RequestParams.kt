package tech.kzen.lib.common.exec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = RequestParamsSerializer::class)
data class RequestParams(
    val values: Map<String, List<String>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = RequestParams(mapOf())


        fun of(vararg entries: Pair<String, String>): RequestParams {
            return RequestParams(
                entries.toMap().mapValues { listOf(it.value) })
        }


        /** Inverse of [asString], total for any input — see the two guards below. */
        fun parse(paramsLine: String): RequestParams {
            val buffer = mutableMapOf<String, MutableList<String>>()

            for (param in paramsLine.split('&')) {
                // Splitting "" yields one blank segment, so the empty params line must produce an empty map.
                // This is [empty]'s own round trip (its asString() is ""), not an exotic input: reading a key off
                // that blank segment ran past the end of the string — throwing on JVM, and on JS silently
                // yielding a bogus "" -> [""] entry, since JS substring clamps instead of failing.
                if (param.isEmpty()) {
                    continue
                }

                val equalsIndex = param.indexOf('=')

                // No '=' is a bare key with no value. asString() never writes that shape, but a parser fed a
                // hand-written line must not run past the end of the string either.
                val key = if (equalsIndex == -1) param else param.substring(0, equalsIndex)
                val value = if (equalsIndex == -1) "" else param.substring(equalsIndex + 1)

                val values = buffer.getOrPut(key) { mutableListOf() }

                values.add(value)
            }

            return RequestParams(buffer)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun set(key: String, value: String): RequestParams {
        return RequestParams(
                values.plus(key to listOf(value)))
    }


    fun get(key: String): String? {
        return values[key].orEmpty().singleOrNull()
    }


    fun getAll(key: String): List<String> {
        return values[key].orEmpty()
    }


    fun replaceValues(find: String, replace: String): RequestParams {
        val builder = mutableMapOf<String, List<String>>()
        for (e in values) {
            val newValues = e.value
                .map {
                    if (it == find) {
                        replace
                    }
                    else {
                        it
                    }
                }
            builder[e.key] = newValues
        }
        return RequestParams(builder)
    }


    fun addAll(addend: RequestParams): RequestParams {
        val buffer = values.mapValues { it.value.toMutableList() }.toMutableMap()

        for (e in addend.values) {
            val valueBuffer = buffer.getOrPut(e.key) { mutableListOf() }
            valueBuffer.addAll(e.value)
        }

        return RequestParams(buffer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        val entries = mutableListOf<String>()
        for (e in values) {
            for (value in e.value) {
                entries.add(e.key + "=" + value)
            }
        }
        return entries.joinToString("&")
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER2: value-object string round-trip (asString()/parse()); bound via @Serializable(with).
object RequestParamsSerializer: KSerializer<RequestParams> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.exec.RequestParams", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RequestParams) {
        encoder.encodeString(value.asString())
    }

    override fun deserialize(decoder: Decoder): RequestParams {
        return RequestParams.parse(decoder.decodeString())
    }
}