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
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


@Serializable(with = AttributeNestingSerializer::class)
data class AttributeNesting(
    val segments: PersistentList<AttributeSegment>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = AttributeNesting(PersistentList())


        fun parse(asString: String): AttributeNesting {
            if (asString == "") {
                return empty
            }

            val segments = AttributePath
                .splitOnDelimiter(asString)
                .map { AttributeSegment.parse(it) }
                .toPersistentList()

            return AttributeNesting(segments)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun shift(): AttributeNesting {
        return AttributeNesting(segments.subList(1, segments.size))
    }


    fun push(segment: AttributeSegment): AttributeNesting {
        return AttributeNesting(segments.add(segment))
    }


    fun push(attributeNesting: AttributeNesting): AttributeNesting {
        return AttributeNesting(
                segments.addAll(attributeNesting.segments))
    }


    fun parent(): AttributeNesting {
        return AttributeNesting(segments.subList(0, segments.size - 1))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(segments)
    }


    fun asString(): String {
        return segments
            .joinToString(AttributePath.delimiter) {
                it.asString()
            }
    }


    override fun toString(): String {
        return asString()
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER2: value-object string round-trip (asString()/parse()); bound via @Serializable(with).
object AttributeNestingSerializer: KSerializer<AttributeNesting> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.model.attribute.AttributeNesting", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttributeNesting) {
        encoder.encodeString(value.asString())
    }

    override fun deserialize(decoder: Decoder): AttributeNesting {
        return AttributeNesting.parse(decoder.decodeString())
    }
}