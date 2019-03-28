package tech.kzen.lib.common.api.model


// TODO: distinguish between DocumentName and DocumentPathSegment?
data class DocumentPath(
//        val segments: List<DocumentName>
        val segments: List<DocumentPathSegment>,
        val name: DocumentName?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val delimiter = "/"

        private val segmentPattern = Regex("[a-zA-Z0-9_\\-]+")
        private val namePattern = Regex("[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+")

//        private val resource = Regex(
//                "([a-zA-Z0-9_\\-]+/)*([a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+)?")


        fun matches(relativeLocation: String): Boolean {
            if (relativeLocation.isEmpty()) {
                return false
            }

            val segments = relativeLocation.split(delimiter)

            val pathMatches = segments
                    .subList(0, segments.size - 1)
                    .all { segmentPattern.matches(it) }
            if (! pathMatches) {
                return false
            }

            val last = segments.last()
            return segmentPattern.matches(last) ||
                    namePattern.matches(last)
        }


        fun parse(asString: String): DocumentPath {
            check(matches(asString)) { "Invalid path: $asString" }

            val parts = asString.split(delimiter)

            if (parts.last().isEmpty()) {
                return DocumentPath(parts.map { DocumentPathSegment(it) }, null)
            }

            val segmentParts = parts.subList(0, parts.size - 1)
            val segments = segmentParts.map { DocumentPathSegment(it) }

            val name = DocumentName(parts.last())

            return DocumentPath(segments, name)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentPath): Boolean {
        check(prefix.name == null) {
            "Name not allowed: $prefix"
        }
        return segments.size >= prefix.segments.size &&
                segments.subList(0, prefix.segments.size) == prefix.segments
    }


    fun parent(): DocumentPath {
        if (name != null) {
            return DocumentPath(segments, null)
        }
        return DocumentPath(segments.subList(0, segments.size - 1), null)
    }


    fun plus(segment: DocumentPathSegment): DocumentPath {
        check(name == null) {
            "Name not allowed: $this"
        }
        return DocumentPath(segments.plus(segment), null)
    }


    fun withName(newName: DocumentName): DocumentPath {
        return DocumentPath(segments, newName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return asRelativeFile()
    }


    fun asRelativeFile(): String {
        val segmentParts = segments.map { it.value }

        val parts =
                if (name == null) {
                    segmentParts.plus("")
                }
                else {
                    segmentParts.plus(name.value)
                }

        return parts.joinToString(delimiter)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}