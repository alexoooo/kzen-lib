package tech.kzen.lib.common.model.attribute

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


// NB: asString()/parse() escape the delimiter ('.' -> '\.'), so serialize via those, never the raw value.
@Serializable(with = AttributeNameSerializer::class)
data class AttributeName(
    val value: String
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): AttributeName {
            return AttributeName(AttributePath.decodeDelimiter(asString))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asAttributePath(): AttributePath {
        return AttributePath.ofName(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }


    fun asString(): String {
        return AttributePath.encodeDelimiter(value)
    }


    override fun toString(): String {
        return asString()
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER2: delimiter-escaped string round-trip (asString()/parse()); bound via @Serializable(with).
object AttributeNameSerializer: KSerializer<AttributeName> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.model.attribute.AttributeName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttributeName) {
        encoder.encodeString(value.asString())
    }

    override fun deserialize(decoder: Decoder): AttributeName {
        return AttributeName.parse(decoder.decodeString())
    }
}