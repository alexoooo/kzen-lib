package tech.kzen.lib.common.api.model


data class AttributePath(
        val attribute: AttributeName,
        val nesting: AttributeNesting
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // TODO: change to "$" or something for better IntelliJ support?
        const val delimiter = "."


        fun indexOfDelimiter(encodedObjectPath: String): Int {
            for (i in 1 until encodedObjectPath.length) {
                if (encodedObjectPath[i] == '.' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return i
                }
            }
            if (! encodedObjectPath.isEmpty() && encodedObjectPath[0] == '.') {
                return 0
            }
            return -1
        }


        fun encodeDelimiter(value: String): String {
            return value.replace(".", "\\.")
        }


        fun decodeDelimiter(value: String): String {
            return value.replace("\\.", ".")
        }


        private fun splitOnDelimiter(encodedObjectPath: String): List<String> {
            val segments = mutableListOf<String>()

            var remaining = encodedObjectPath

            while (true) {
                val nextIndex = indexOfDelimiter(remaining)
                if (nextIndex == -1) {
                    segments.add(remaining)
                    break
                }

                segments.add(remaining.substring(0, nextIndex))

                remaining = remaining.substring(nextIndex + 1)
            }

            return segments
        }


        fun ofAttribute(attribute: AttributeName): AttributePath {
            return AttributePath(attribute, AttributeNesting.empty)
        }


        fun parse(asString: String): AttributePath {
            val parts = splitOnDelimiter(asString)

            val attribute = AttributeName(decodeDelimiter(parts[0]))
            val segments = parts.subList(1, parts.size).map { AttributeSegment.parse(it) }

            return AttributePath(attribute, AttributeNesting(segments))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        val segmentSuffix =
                if (nesting.segments.isEmpty()) {
                    ""
                }
                else {
                    delimiter + nesting.segments.joinToString(delimiter) { it.asString() }
                }

        return encodeDelimiter(attribute.value) + segmentSuffix
    }


    fun parent(): AttributePath {
        return AttributePath(attribute, nesting.parent())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}