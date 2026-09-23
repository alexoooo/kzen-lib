package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


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
    // Cached: a hot map key over immutable parts, whose generated hash re-walked its segment lists per lookup.
    // Racy but safe: an Int write can't tear, and a lost race recomputes the same value.
    private var hash = 0


    override fun hashCode(): Int {
        var result = hash
        if (result == 0) {
            result = 31 * objectName.hashCode() + attributePath.hashCode()
            hash = result
        }
        return result
    }


    override fun digest(sink: Digest.Sink) {
        objectName.digest(sink)
        attributePath.digest(sink)
    }


    override fun toString(): String {
        return asString()
    }
}