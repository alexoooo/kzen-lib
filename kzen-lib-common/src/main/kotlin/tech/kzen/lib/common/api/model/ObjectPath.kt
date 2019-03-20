package tech.kzen.lib.common.api.model


data class ObjectPath(
        val name: ObjectName,
        val nesting: DocumentNesting
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ObjectPath {
            val nameSuffix = DocumentNesting.extractNameSuffix(asString)
            val segmentsAsString = DocumentNesting.extractSegments(asString)

            val name = ObjectName(nameSuffix)
            val nesting =
                    if (segmentsAsString == null) {
                        DocumentNesting.root
                    }
                    else {
                        DocumentNesting.parse(segmentsAsString)
                    }

            return ObjectPath(name, nesting)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        if (nesting.segments.isEmpty()) {
            return DocumentNesting.encodeDelimiter(name.value)
        }
        return nesting.asString() +
                DocumentNesting.delimiter +
                DocumentNesting.encodeDelimiter(name.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun nest(attributePath: AttributePath, nestedName: ObjectName): ObjectPath {
        val nestSegment = nesting.append(DocumentNestingSegment(name, attributePath))
        return ObjectPath(nestedName, nestSegment)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}