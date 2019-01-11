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

            throw UnsupportedOperationException("Not implemented (yet)")

//            val parts = asString.split(delimiter)
//
//            val segments: List<BundleNestingSegment> =
//                    if (parts.isEmpty()) {
//                        listOf()
//                    }
//                    else {
////                        parts.subList(0, parts.size).map { BundleNestingSegment(it) }
//                        throw UnsupportedOperationException("Not implemented (yet)")
//                    }
//
//            return BundleNesting(segments)
        }
    }


    fun asString(): String {
        if (segments.isEmpty()) {
            return ""
        }
        return segments.map { it.asString() }.joinToString { delimiter }
    }
}