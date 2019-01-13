package tech.kzen.lib.common.api.model


data class AttributeNesting(
        val attribute: AttributeName,
        val segments: List<AttributeSegment>
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


        fun ofAttribute(attribute: AttributeName): AttributeNesting {
            return AttributeNesting(attribute, listOf())
        }

        fun parse(asString: String): AttributeNesting {
            val parts = asString.split(delimiter)

            val attribute = AttributeName(parts[0])
            val segments = parts.subList(1, parts.size).map { AttributeSegment.parse(it) }

            return AttributeNesting(attribute, segments)
        }
    }


    fun asString(): String {
        val segmentSuffix =
                if (segments.isEmpty()) {
                    ""
                }
                else {
                    delimiter + segments.map { it.asString() }.joinToString(delimiter)
                }

        return attribute.value + segmentSuffix
    }


    override fun toString(): String {
        return asString()
    }
}