package tech.kzen.lib.common.api.model


data class AttributePath(
        val attribute: AttributeName,
        val nesting: AttributeNesting
) {
    companion object {
//        fun ofAttribute(attribute: AttributeName): AttributeNesting {
//            return AttributeNesting(attribute = attribute, segments = listOf())
//        }

        const val delimiter = "."

//        fun ofDelimited(notationPath: String): ObjectNotationPath {
//            val segments = notationPath.split(delimiter)
//            return ObjectNotationPath(segments)
//        }
//
//        fun ofSegments(vararg segments: String): ObjectNotationPath {
//            return ObjectNotationPath(segments.toList())
//        }


        fun ofAttribute(attribute: AttributeName): AttributePath {
            return AttributePath(attribute, AttributeNesting.empty)
        }

        fun parse(asString: String): AttributePath {
            val parts = asString.split(delimiter)

            val attribute = AttributeName(parts[0])
            val segments = parts.subList(1, parts.size).map { AttributeSegment.parse(it) }

            return AttributePath(attribute, AttributeNesting(segments))
        }
    }


    fun asString(): String {
        val segmentSuffix =
                if (nesting.segments.isEmpty()) {
                    ""
                }
                else {
                    delimiter + nesting.segments.joinToString(delimiter) { it.asString() }
                }

        return attribute.value + segmentSuffix
    }


    override fun toString(): String {
        return asString()
    }
}