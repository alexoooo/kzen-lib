package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


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