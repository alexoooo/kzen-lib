package tech.kzen.lib.common.model.document


data class DocumentPath(
        val name: DocumentName?,
        val nesting: DocumentNesting
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val delimiter = "/"

        private val segmentPattern = Regex("[a-zA-Z0-9_\\- ]+")
        private val namePattern = Regex("[a-zA-Z0-9_\\- ]+\\.yaml")

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
                return DocumentPath(
                        null,
                        DocumentNesting(parts.map { DocumentSegment(it) }))
            }

            val segmentParts = parts.subList(0, parts.size - 1)
            val segments = segmentParts.map { DocumentSegment(it) }

            val name = DocumentName(parts.last())

            return DocumentPath(name, DocumentNesting(segments))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentPath): Boolean {
        check(prefix.name == null) {
            "Name not allowed: $prefix"
        }
        return nesting.startsWith(prefix.nesting)
    }


    fun parent(): DocumentPath {
        if (name != null) {
            return DocumentPath(null, nesting)
        }
        return DocumentPath(null, nesting.parent())
    }


    fun plus(segment: DocumentSegment): DocumentPath {
        check(name == null) {
            "Name not allowed: $this"
        }
        return DocumentPath(null, nesting.plus(segment))
    }


    fun withName(newName: DocumentName): DocumentPath {
        return DocumentPath(newName, nesting)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return asRelativeFile()
    }


    fun asRelativeFile(): String {
        val segmentParts = nesting.segments.map { it.value }

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