package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.toPersistentList


data class DocumentNesting(
        val segments: PersistentList<DocumentSegment>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        const val delimiter = "/"
        val empty = DocumentNesting(persistentListOf())


        fun matches(relativeLocation: String): Boolean {
            if (relativeLocation.isEmpty()) {
                return true
            }

            val segments = relativeLocation.split(NotationConventions.pathDelimiter)

            val pathMatches = segments
                    .subList(0, segments.size - 1)
                    .all { DocumentSegment.segmentPattern.matches(it) }
            if (! pathMatches) {
                return false
            }

            val last = segments.last()
            return DocumentSegment.segmentPattern.matches(last) ||
                    last.isEmpty()
        }


        fun parse(asString: String): DocumentNesting {
            check(matches(asString)) { "Invalid path: $asString" }

            val segments = asString.split(NotationConventions.pathDelimiter)

            val usedSegments =
                    if (segments.last().isEmpty()) {
                        segments.subList(0, segments.size - 1)
                    }
                    else {
                        segments
                    }

            return DocumentNesting(usedSegments.map {
                DocumentSegment(it)
            }.toPersistentList())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentNesting): Boolean {
        return segments.size >= prefix.segments.size &&
                segments.subList(0, prefix.segments.size) == prefix.segments
    }


    fun parent(): DocumentNesting {
        return DocumentNesting(segments.subList(0, segments.size - 1))
    }


    fun plus(segment: DocumentSegment): DocumentNesting {
        return DocumentNesting(segments.add(segment))
    }


    fun asString(): String {
        return segments.joinToString(NotationConventions.pathDelimiter) { it.value }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleList(segments)
    }
}