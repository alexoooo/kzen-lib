package tech.kzen.lib.common.model.obj

import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.toPersistentList


/**
 * Attribute nesting within an object
 */
data class ObjectNesting(
        val segments: PersistentList<ObjectNestingSegment>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val delimiter = "/"
//        val delimiterRegex = Regex("[^\\\\]/")

        val root = ObjectNesting(PersistentList())


        private fun containsSegments(encodedObjectPath: String): Boolean {
            for (i in 1 until encodedObjectPath.length) {
                if (encodedObjectPath[i] == '/' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return true
                }
            }
            return encodedObjectPath[0] == '/'
        }


        private fun lastIndexOfDelimiter(encodedObjectPath: String): Int {
            for (i in encodedObjectPath.length - 1 downTo 1) {
                if (encodedObjectPath[i] == '/' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return i
                }
            }
            if (encodedObjectPath.isNotEmpty() && encodedObjectPath[0] == '/') {
                return 0
            }
            return -1
        }


        private fun indexOfDelimiter(encodedObjectPath: String): Int {
            for (i in 1 until encodedObjectPath.length) {
                if (encodedObjectPath[i] == '/' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return i
                }
            }
            if (encodedObjectPath.isNotEmpty() && encodedObjectPath[0] == '/') {
                return 0
            }
            return -1
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


        fun encodeDelimiter(value: String): String {
            return value.replace("/", "\\/")
        }


        fun decodeDelimiter(value: String): String {
            return value.replace("\\/", "/")
        }


        fun extractNameSuffix(encodedObjectPath: String): String {
            if (! containsSegments(encodedObjectPath)) {
                return decodeDelimiter(encodedObjectPath)
            }
            val startOfSuffix = lastIndexOfDelimiter(encodedObjectPath)
            val encodedName = encodedObjectPath.substring(startOfSuffix + delimiter.length)
            return decodeDelimiter(encodedName)
        }


//        fun encodeNameSuffix(objectName: ObjectName): String {
//            return objectName
//        }


        fun extractSegments(encodedObjectPath: String): String? {
            if (! containsSegments(encodedObjectPath)) {
                return null
            }
            val startOfSuffix = lastIndexOfDelimiter(encodedObjectPath)
            return encodedObjectPath.substring(0, startOfSuffix)
        }


        fun parse(asString: String): ObjectNesting {
            if (asString.isEmpty()) {
                return root
            }

            val parts = splitOnDelimiter(asString)
//            check(parts.size % 2 == 0) { "Name/nesting segment mis-match: $asString" }

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


    fun startswith(objectNesting: ObjectNesting): Boolean {
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
    override fun toString(): String {
        return asString()
    }
}