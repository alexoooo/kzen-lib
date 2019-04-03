package tech.kzen.lib.common.model.document


data class DocumentNesting(
        val segments: List<DocumentSegment>
) {
    fun startsWith(prefix: DocumentNesting): Boolean {
        return segments.size >= prefix.segments.size &&
                segments.subList(0, prefix.segments.size) == prefix.segments
    }


    fun parent(): DocumentNesting {
        return DocumentNesting(segments.subList(0, segments.size - 1))
    }


    fun plus(segment: DocumentSegment): DocumentNesting {
        return DocumentNesting(segments.plus(segment))
    }
}