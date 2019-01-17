package tech.kzen.lib.common.api.model


data class BundleNesting(
        val segments: List<BundleNestingSegment>
) {
    companion object {
        const val delimiter = "/"

        val root = BundleNesting(listOf())


        private fun containsSegments(objectPathAsString: String): Boolean {
            return objectPathAsString.contains(delimiter)
        }


        fun extractNameSuffix(objectPathAsString: String): String {
            if (! containsSegments(objectPathAsString)) {
                return objectPathAsString
            }
            val startOfSuffix = objectPathAsString.lastIndexOf(delimiter)
            return objectPathAsString.substring(startOfSuffix + delimiter.length)
        }


        fun extractSegments(objectPathAsString: String): String? {
            if (! containsSegments(objectPathAsString)) {
                return null
            }
            val startOfSuffix = objectPathAsString.lastIndexOf(delimiter)
            return objectPathAsString.substring(0, startOfSuffix)
        }


        fun parse(asString: String): BundleNesting {
            if (asString.isEmpty()) {
                return root
            }

            val parts = asString.split(delimiter)
            check(parts.size % 2 == 0) { "Name/nesting segment mis-match: $asString" }

            val builder = mutableListOf<BundleNestingSegment>()
            for (i in 0 until parts.size step 2) {
                builder.add(BundleNestingSegment(
                        ObjectName(parts[i]),
                        AttributePath.parse(parts[i + 1])))
            }
            return BundleNesting(builder)
        }
    }


    fun asString(): String {
        if (segments.isEmpty()) {
            return ""
        }
        return segments.map { it.asString() }.joinToString { delimiter }
    }
}