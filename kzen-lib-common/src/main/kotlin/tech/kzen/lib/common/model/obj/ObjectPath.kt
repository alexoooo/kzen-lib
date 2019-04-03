package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.attribute.AttributePath


/**
 * Path to an object within a document
 */
data class ObjectPath(
        val name: ObjectName,
        val nesting: ObjectNesting
) {
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
    fun nest(attributePath: AttributePath, nestedName: ObjectName): ObjectPath {
        val nestSegment = nesting.append(ObjectNestingSegment(name, attributePath))
        return ObjectPath(nestedName, nestSegment)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}