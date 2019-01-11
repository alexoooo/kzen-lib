package tech.kzen.lib.common.api.model


data class BundlePath(
//        val relativeLocation: String
        val segments: List<String>
) {
    companion object {
        private val delimiter = "/"

        private val resource = Regex(
                "([a-zA-Z0-9_\\-]+/)*[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+")


        fun matches(relativeLocation: String): Boolean {
            return ! relativeLocation.startsWith(delimiter) &&
                    resource.matches(relativeLocation)
        }


        fun parse(asString: String): BundlePath {
            check(matches(asString)) { "Invalid path: $asString" }

            val segments = asString.split(delimiter)
            return BundlePath(segments)
        }
    }


    fun asString(): String {
        return asRelativeFile()
    }

    fun asRelativeFile(): String {
        return segments.joinToString(delimiter)
    }


//    init {
//        if (!matches(relativeLocation)) {
//            throw IllegalArgumentException(
//                    "Not a valid project path: $relativeLocation")
//        }
//    }


//    fun segments(): List<String> {
//        return relativeLocation.split("/")
//    }
}