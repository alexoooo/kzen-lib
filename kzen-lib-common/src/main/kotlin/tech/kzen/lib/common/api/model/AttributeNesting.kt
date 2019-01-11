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

//        fun parse(asString: String): AttributeNesting {
//            val parts = asString.split(delimiter)
//
//            val attribute = AttributeName(parts[0])
//
//            parts.subList(1, parts.size).map {  }
//
//            if (parts.size == 1) {
//                return AttributeNesting(
//                        ,
//                        listOf())
//            }
//
//            for (i in 1 until parts.size) {
//
//            }
//        }
    }


    fun asString(): String {
        val segmentSuffix =
                if (segments.isEmpty()) {
                    ""
                }
                else {
                    delimiter + segments.map { it.asString() }.joinToString { delimiter }
                }

        return attribute.value + segmentSuffix
    }
}