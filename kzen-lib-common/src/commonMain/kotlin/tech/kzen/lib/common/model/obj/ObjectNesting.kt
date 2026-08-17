package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.common.util.naming.EscapedDelimiter
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


/**
 * Attribute nesting within an object
 */
data class ObjectNesting(
    val segments: PersistentList<ObjectNestingSegment>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val delimiter = "/"

        private val escapedDelimiter = EscapedDelimiter('/')

        val root = ObjectNesting(PersistentList())


        fun encodeDelimiter(value: String): String {
            return escapedDelimiter.encode(value)
        }


        fun decodeDelimiter(value: String): String {
            return escapedDelimiter.decode(value)
        }


        fun extractNameSuffix(encodedObjectPath: String): String {
            if (!escapedDelimiter.contains(encodedObjectPath)) {
                return decodeDelimiter(encodedObjectPath)
            }
            val startOfSuffix = escapedDelimiter.lastIndexOf(encodedObjectPath)
            val encodedName = encodedObjectPath.substring(startOfSuffix + delimiter.length)
            return decodeDelimiter(encodedName)
        }


        fun extractSegments(encodedObjectPath: String): String? {
            if (!escapedDelimiter.contains(encodedObjectPath)) {
                return null
            }
            val startOfSuffix = escapedDelimiter.lastIndexOf(encodedObjectPath)
            return encodedObjectPath.substring(0, startOfSuffix)
        }


        fun parse(asString: String): ObjectNesting {
            if (asString.isEmpty()) {
                return root
            }

            val parts = escapedDelimiter.split(asString)

            val builder = mutableListOf<ObjectNestingSegment>()
            for ((index, part) in parts.withIndex()) {
                if (index == 0 && part.isEmpty()) {
                    // absolute path
                    continue
                }

                builder.add(ObjectNestingSegment.parse(part))
            }
            return ObjectNesting(builder.toPersistentList())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isRoot(): Boolean {
        return segments.isEmpty()
    }


    fun startsWith(objectNesting: ObjectNesting): Boolean {
        if (segments.size < objectNesting.segments.size) {
            return false
        }

        if (segments.size == objectNesting.segments.size) {
            return segments == objectNesting.segments
        }

        return segments.subList(0, objectNesting.segments.size) == objectNesting.segments
    }


    fun parent(): ObjectPath? {
        if (segments.isEmpty()) {
            return null
        }

        val leadingSegments = segments.subList(0, segments.size - 1)
        val lastSegment = segments.last()

        return ObjectPath(
                lastSegment.objectName,
                ObjectNesting(leadingSegments.toPersistentList())
        )
    }


    fun append(segment: ObjectNestingSegment): ObjectNesting {
        return ObjectNesting(segments.add(segment))
    }


    fun asString(): String {
        if (segments.isEmpty()) {
            return ""
        }
        return segments.joinToString(delimiter) { it.asString() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleList(segments)
    }


    override fun toString(): String {
        return asString()
    }
}