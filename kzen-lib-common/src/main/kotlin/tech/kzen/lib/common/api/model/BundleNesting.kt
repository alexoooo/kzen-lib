package tech.kzen.lib.common.api.model


data class BundleNesting(
        val segments: List<BundleNestingSegment>
) {
    companion object {
        val delimiter = "/"
//        val delimiterRegex = Regex("[^\\\\]/")

        val root = BundleNesting(listOf())


        private fun containsSegments(encodedObjectPath: String): Boolean {
            for (i in 1 until encodedObjectPath.length) {
                if (encodedObjectPath[i] == '/' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return true
                }
            }
            return false
        }


        private fun lastIndexOfDelimiter(encodedObjectPath: String): Int {
            for (i in encodedObjectPath.length - 1 downTo 1) {
                if (encodedObjectPath[i] == '/' &&
                        encodedObjectPath[i - 1] != '\\') {
                    return i
                }
            }
            if (! encodedObjectPath.isEmpty() && encodedObjectPath[0] == '/') {
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
            if (! encodedObjectPath.isEmpty() && encodedObjectPath[0] == '/') {
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


        fun extractSegments(encodedObjectPath: String): String? {
            if (! containsSegments(encodedObjectPath)) {
                return null
            }
            val startOfSuffix = lastIndexOfDelimiter(encodedObjectPath)
            return encodedObjectPath.substring(0, startOfSuffix)
        }


        fun parse(asString: String): BundleNesting {
            if (asString.isEmpty()) {
                return root
            }

            val parts = splitOnDelimiter(asString)
//            check(parts.size % 2 == 0) { "Name/nesting segment mis-match: $asString" }

            val builder = mutableListOf<BundleNestingSegment>()
            for (part in parts) {
                builder.add(BundleNestingSegment.parse(part))
            }
            return BundleNesting(builder)
        }
    }


    fun append(segment: BundleNestingSegment): BundleNesting {
        return BundleNesting(segments.plus(segment))
    }


    fun asString(): String {
        if (segments.isEmpty()) {
            return ""
        }
        return segments.joinToString(delimiter) { it.asString() }
    }
}