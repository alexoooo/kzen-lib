package tech.kzen.lib.common.api.model


data class DocumentPath(
        val segments: List<DocumentName>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val delimiter = "/"

        private val pathSegment = Regex("[a-zA-Z0-9_\\-]+")
        private val resourceSegment = Regex("[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+")

//        private val resource = Regex(
//                "([a-zA-Z0-9_\\-]+/)*([a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+)?")


        fun matches(relativeLocation: String): Boolean {
            if (relativeLocation.isEmpty()) {
                return false
            }

            val segments = relativeLocation.split(delimiter)

            val pathMatches = segments
                    .subList(0, segments.size - 1)
                    .all { pathSegment.matches(it) }
            if (! pathMatches) {
                return false
            }

            val last = segments.last()
            return pathSegment.matches(last) ||
                    resourceSegment.matches(last)
        }


        fun parse(asString: String): DocumentPath {
            check(matches(asString)) { "Invalid path: $asString" }

            val segments = asString.split(delimiter).map { DocumentName.parse(it) }
            return DocumentPath(segments)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentPath): Boolean {
        return segments.size >= prefix.segments.size &&
                segments.subList(0, prefix.segments.size) == prefix.segments
    }


    fun parent(): DocumentPath {
        return DocumentPath(segments.subList(0, segments.size - 1))
    }


    fun plus(subName: DocumentName): DocumentPath {
        return DocumentPath(segments.plus(subName))
    }


    fun withName(newName: DocumentName): DocumentPath {
        return parent().plus(newName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return asRelativeFile()
    }

    fun asRelativeFile(): String {
        return segments.joinToString(delimiter)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}