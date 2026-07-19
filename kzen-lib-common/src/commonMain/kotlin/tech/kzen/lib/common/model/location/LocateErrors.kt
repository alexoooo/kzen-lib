package tech.kzen.lib.common.model.location


/**
 * Failed-lookup message for the [ObjectLocator] implementations. Instead of dumping every document path in
 *  the graph (which said nothing about the reference that failed), name the near misses: objects that carry
 *  the same name but sit at a different document or nesting. Same-name-only - no fuzzy matching.
 *
 * Only ever built on the throw path, from an index the locator already maintains.
 */
internal object LocateErrors {
    //-----------------------------------------------------------------------------------------------------------------
    private const val candidateCap = 5


    //-----------------------------------------------------------------------------------------------------------------
    fun missingMessage(
        reference: ObjectReference,
        host: ObjectReferenceHost?,
        sameNameCandidates: List<ObjectLocation>,
        totalObjectCount: Int
    ): String {
        val header = buildString {
            append("Missing: ")
            append(reference)
            if (host != null && host != ObjectReferenceHost.global) {
                append(" (host: ")
                append(host)
                append(")")
            }
        }

        val objectName = reference.name.objectName
            ?: return "$header; reference is empty"

        if (sameNameCandidates.isEmpty()) {
            return "$header; no object named '${objectName.value}' among $totalObjectCount objects"
        }

        // a candidate in the document the reference was scoped to is the closest miss (nesting differs)
        val scopedDocumentPath = reference.path ?: host?.documentPath
        val ordered = sameNameCandidates.sortedBy { it.documentPath != scopedDocumentPath }

        val shown = ordered
            .take(candidateCap)
            .joinToString(", ") { it.asString() }

        val overflow = ordered.size - candidateCap

        return if (overflow > 0) {
            "$header; same name at: $shown, +$overflow more"
        }
        else {
            "$header; same name at: $shown"
        }
    }
}
