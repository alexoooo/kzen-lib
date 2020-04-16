package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


/**
 * Path to an object within a document
 */
data class ObjectPath(
        val name: ObjectName,
        val nesting: ObjectNesting
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ObjectPath {
            val nameSuffix = ObjectNesting.extractNameSuffix(asString)
            val segmentsAsString = ObjectNesting.extractSegments(asString)

            val name = ObjectName(nameSuffix)
            val nesting =
                    if (segmentsAsString == null) {
                        ObjectNesting.root
                    }
                    else {
                        ObjectNesting.parse(segmentsAsString)
                    }

            return ObjectPath(name, nesting)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        if (nesting.segments.isEmpty()) {
            return ObjectNesting.encodeDelimiter(name.value)
        }
        return nesting.asString() +
                ObjectNesting.delimiter +
                ObjectNesting.encodeDelimiter(name.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(objectPath: ObjectPath): Boolean {
        return nesting.segments.size > objectPath.nesting.segments.size &&
                nesting.startswith(objectPath.nesting) &&
                nesting.segments[
                        objectPath.nesting.segments.size
                ].objectName == objectPath.name
    }


    fun nest(attributePath: AttributePath, nestedName: ObjectName): ObjectPath {
        val nestSegment = nesting.append(ObjectNestingSegment(name, attributePath))
        return ObjectPath(nestedName, nestSegment)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        name.digest(builder)
        nesting.digest(builder)
    }


    override fun toString(): String {
        return asString()
    }
}