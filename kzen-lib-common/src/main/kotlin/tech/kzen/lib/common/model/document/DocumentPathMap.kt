package tech.kzen.lib.common.model.document


data class DocumentPathMap<T>(
        val values: Map<DocumentPath, T>
) {
    fun get(documentPath: DocumentPath): T {
        return values[documentPath]
                ?: throw IllegalArgumentException("Missing: $documentPath")
    }

    override fun toString(): String {
        return values.toString()
    }
}