package tech.kzen.lib.common.model.document

import tech.kzen.lib.platform.collect.PersistentMap


data class DocumentPathMap<T>(
        val values: PersistentMap<DocumentPath, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    operator fun contains(documentPath: DocumentPath): Boolean {
        return values.containsKey(documentPath)
    }


    operator fun get(documentPath: DocumentPath): T? {
        return values[documentPath]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(documentPath: DocumentPath, instance: T): DocumentPathMap<T> {
        return DocumentPathMap(
                values.put(documentPath, instance))
    }


    fun remove(documentPath: DocumentPath): DocumentPathMap<T> {
        return DocumentPathMap(
                values.remove(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return values.toString()
    }
}