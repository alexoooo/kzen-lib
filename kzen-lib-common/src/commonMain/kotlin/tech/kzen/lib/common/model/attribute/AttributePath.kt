package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


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


        fun splitOnDelimiter(encodedObjectPath: String): List<String> {
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
            val firstDelimiterIndex = indexOfDelimiter(asString)
            if (firstDelimiterIndex == -1) {
                val attributeName = AttributeName.parse(asString)
                return ofName(attributeName)
            }

            val attributeNameAsString = asString.substring(0, firstDelimiterIndex)
            val attributeName = AttributeName.parse(attributeNameAsString)

            val attributeNestingAsString = asString.substring(firstDelimiterIndex + 1)
            val attributeNesting = AttributeNesting.parse(attributeNestingAsString)

            return AttributePath(attributeName, attributeNesting)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parent(): AttributePath {
        return AttributePath(attribute, nesting.parent())
    }


    fun nest(attributeSegment: AttributeSegment): AttributePath {
        return copy(nesting = nesting.push(attributeSegment))
    }


    fun nest(attributeNesting: AttributeNesting): AttributePath {
        return copy(nesting = nesting.push(attributeNesting))
    }


    fun toNesting(): AttributeNesting {
        return AttributeNesting(
            nesting.segments.add(0, AttributeSegment.ofKey(attribute.value)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        attribute.digest(sink)
        nesting.digest(sink)
    }


    fun asString(): String {
        val attributeNameAsString = attribute.asString()
        if (nesting.segments.isEmpty()) {
            return attributeNameAsString
        }

        val attributeNestingAsString = nesting.asString()

        return attributeNameAsString + delimiter + attributeNestingAsString
    }


    override fun toString(): String {
        return asString()
    }
}