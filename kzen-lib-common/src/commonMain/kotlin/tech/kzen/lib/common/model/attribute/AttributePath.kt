package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.toPersistentList


data class AttributePath(
        val attribute: AttributeName,
        val nesting: AttributeNesting
):
        Digestible
{
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
            if (encodedObjectPath.isNotEmpty() && encodedObjectPath[0] == '.') {
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


        fun ofName(attributeName: AttributeName): AttributePath {
            return AttributePath(attributeName, AttributeNesting.empty)
        }


        fun parse(asString: String): AttributePath {
            val parts = splitOnDelimiter(asString)

            val attribute = AttributeName(decodeDelimiter(parts[0]))
            val segments = parts.subList(1, parts.size).map { AttributeSegment.parse(it) }

            return AttributePath(attribute, AttributeNesting(segments.toPersistentList()))
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


    fun nest(attributeSegment: AttributeSegment): AttributePath {
        return copy(nesting = nesting.push(attributeSegment))
    }


    fun nest(attributeNesting: AttributeNesting): AttributePath {
        return copy(nesting = nesting.push(attributeNesting))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        attribute.digest(builder)
        nesting.digest(builder)
    }


    override fun toString(): String {
        return asString()
    }
}