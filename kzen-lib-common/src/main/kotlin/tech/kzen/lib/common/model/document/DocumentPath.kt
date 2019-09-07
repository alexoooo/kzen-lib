package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DocumentPath(
        val name: DocumentName,
        val nesting: DocumentNesting,
        val directory: Boolean
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private val resource = Regex(
//                "([a-zA-Z0-9_\\-]+/)*([a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]+)?")


        fun matches(relativeLocation: String): Boolean {
            if (! relativeLocation.endsWith(NotationConventions.fileDocumentSuffix)) {
                return false
            }

            val normalizedLocation =
                    if (relativeLocation.endsWith(NotationConventions.directoryDocumentSuffix)) {
                        relativeLocation.substring(
                                0, relativeLocation.length - NotationConventions.directoryDocumentSuffix.length)
                    }
                    else {
                        relativeLocation.substring(
                                0, relativeLocation.length - NotationConventions.fileDocumentSuffix.length)
                    }

            val endOfNesting = normalizedLocation.lastIndexOf(NotationConventions.pathDelimiter)
            if (endOfNesting == -1) {
                return DocumentName.matches(normalizedLocation)
            }

            val nestingPrefix = normalizedLocation.substring(0, endOfNesting)
            val nameSuffix = normalizedLocation.substring(endOfNesting + 1)

            return DocumentNesting.matches(nestingPrefix) &&
                    DocumentName.matches(nameSuffix)
        }


        fun parse(asString: String): DocumentPath {
            check(matches(asString)) { "Invalid path: $asString" }

            val directory = asString.endsWith(NotationConventions.directoryDocumentSuffix)
            val normalizedLocation =
                    if (directory) {
                        asString.substring(0, asString.length - NotationConventions.directoryDocumentSuffix.length)
                    }
                    else {
                        asString.substring(0, asString.length - NotationConventions.fileDocumentSuffix.length)
                    }

            val endOfNesting = normalizedLocation.lastIndexOf(NotationConventions.pathDelimiter)
            if (endOfNesting == -1) {
                return DocumentPath(
                        DocumentName(normalizedLocation),
                        DocumentNesting.empty,
                        directory)
            }

            val nestingPrefix = normalizedLocation.substring(0, endOfNesting)
            val nameSuffix = normalizedLocation.substring(endOfNesting + 1)

            return DocumentPath(
                    DocumentName(nameSuffix),
                    DocumentNesting.parse(nestingPrefix),
                    directory)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentNesting): Boolean {
        return nesting.startsWith(prefix)
    }
//    fun startsWith(prefix: DocumentPath): Boolean {
//        check(prefix.name == null) {
//            "Name not allowed: $prefix"
//        }
//        return nesting.startsWith(prefix.nesting)
//    }


//    fun parent(): DocumentPath {
//        if (name != null) {
//            return DocumentPath(null, nesting, false)
//        }
//        return DocumentPath(null, nesting.parent(), false)
//    }


//    fun plus(segment: DocumentSegment): DocumentPath {
//        check(name == null) {
//            "Name not allowed: $this"
//        }
//        return DocumentPath(null, nesting.plus(segment), false)
//    }


    fun withName(newName: DocumentName): DocumentPath {
        return copy(name = newName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return asRelativeFile()
    }


    fun asRelativeFile(): String {
        val nestingPrefix =
                if (nesting.segments.isEmpty()) {
                    ""
                }
                else {
                    nesting.asString() + "/"
                }

        val formatSuffix =
                if (directory) {
                    NotationConventions.directoryDocumentSuffix
                }
                else {
                    NotationConventions.fileDocumentSuffix
                }

        return nestingPrefix +
                name.value +
                formatSuffix
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Builder) {
        digester.addDigestible(name)
        digester.addDigestible(nesting)
        digester.addBoolean(directory)
    }


    override fun toString(): String {
        return asString()
    }
}