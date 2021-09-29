package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ObjectNestingSegment(
        val objectName: ObjectName,
        val attributePath: AttributePath
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ObjectNestingSegment {
            val nameDelimiter = AttributePath.indexOfDelimiter(asString)
            check(nameDelimiter != -1) {
                "Object nesting expected: $asString"
            }

            val encodedName = asString.substring(0, nameDelimiter)
            val attributePathSuffix = asString.substring(nameDelimiter + 1)

            return ObjectNestingSegment(
                    ObjectName(AttributePath.decodeDelimiter(encodedName)),
                    AttributePath.parse(attributePathSuffix))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return ObjectNesting.encodeDelimiter(objectName.value) +
                AttributePath.delimiter +
                attributePath.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        objectName.digest(sink)
        attributePath.digest(sink)
    }


    override fun toString(): String {
        return asString()
    }
}