package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class DocumentPath(
    val name: DocumentName,
    val nesting: DocumentNesting,
    val form: DocumentForm
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Back-compat constructor: callers that only know "document vs directory-document" keep passing a Boolean
        // (folders are created with the explicit DocumentForm.Folder).
        fun matches(relativeLocation: String): Boolean {
            val normalized = stripFormatSuffix(relativeLocation)
                ?: return false

            val endOfNesting = normalized.lastIndexOf(NotationConventions.pathDelimiter)
            if (endOfNesting == -1) {
                return DocumentName.matches(normalized)
            }

            val nestingPrefix = normalized.substring(0, endOfNesting)
            val nameSuffix = normalized.substring(endOfNesting + 1)

            return DocumentNesting.matches(nestingPrefix) &&
                    DocumentName.matches(nameSuffix)
        }


        fun parse(asString: String): DocumentPath {
            check(matches(asString)) { "Invalid path: $asString" }

            val form = formOf(asString)
            val normalizedLocation = stripFormatSuffix(asString)!!

            val endOfNesting = normalizedLocation.lastIndexOf(NotationConventions.pathDelimiter)
            if (endOfNesting == -1) {
                return DocumentPath(
                    DocumentName(normalizedLocation),
                    DocumentNesting.empty,
                    form)
            }

            val nestingPrefix = normalizedLocation.substring(0, endOfNesting)
            val nameSuffix = normalizedLocation.substring(endOfNesting + 1)

            return DocumentPath(
                DocumentName(nameSuffix),
                DocumentNesting.parse(nestingPrefix),
                form)
        }


        private fun formOf(relativeLocation: String): DocumentForm {
            return when {
                relativeLocation.endsWith(NotationConventions.directoryDocumentSuffix) -> DocumentForm.Directory
                relativeLocation.endsWith(NotationConventions.fileDocumentSuffix) -> DocumentForm.Document
                relativeLocation.endsWith(NotationConventions.pathDelimiter) -> DocumentForm.Folder
                else -> error("Not a document path: $relativeLocation")
            }
        }


        // Removes the format suffix, returning "<nesting>/<name>" (or "<name>"), or null if the string isn't a
        // valid document/directory-document/folder path.
        private fun stripFormatSuffix(relativeLocation: String): String? {
            return when {
                relativeLocation.endsWith(NotationConventions.directoryDocumentSuffix) ->
                    relativeLocation.substring(
                        0, relativeLocation.length - NotationConventions.directoryDocumentSuffix.length)

                relativeLocation.endsWith(NotationConventions.fileDocumentSuffix) ->
                    relativeLocation.substring(
                        0, relativeLocation.length - NotationConventions.fileDocumentSuffix.length)

                relativeLocation.endsWith(NotationConventions.pathDelimiter) ->
                    relativeLocation.substring(0, relativeLocation.length - NotationConventions.pathDelimiter.length)

                else -> null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    constructor(
        name: DocumentName,
        nesting: DocumentNesting,
        directory: Boolean
    ): this(
        name,
        nesting,
        if (directory) DocumentForm.Directory else DocumentForm.Document)


    // NB: a directory-DOCUMENT (e.g. Feature) — NOT a pure folder. Preserved so existing read-sites are unchanged.
    val directory: Boolean
        get() = form == DocumentForm.Directory

    val folder: Boolean
        get() = form == DocumentForm.Folder


    //-----------------------------------------------------------------------------------------------------------------
    fun startsWith(prefix: DocumentNesting): Boolean {
        return nesting.startsWith(prefix)
    }


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
            when (form) {
                DocumentForm.Document -> NotationConventions.fileDocumentSuffix
                DocumentForm.Directory -> NotationConventions.directoryDocumentSuffix
                DocumentForm.Folder -> NotationConventions.pathDelimiter
            }

        return nestingPrefix +
                name.value +
                formatSuffix
    }


    fun toMainObjectLocation(): ObjectLocation {
        return toObjectLocation(NotationConventions.mainObjectPath)
    }


    fun toObjectLocation(objectPath: ObjectPath): ObjectLocation {
        return ObjectLocation(this, objectPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(name)
        sink.addDigestible(nesting)
        sink.addInt(form.ordinal)
    }


    override fun toString(): String {
        return asString()
    }
}
