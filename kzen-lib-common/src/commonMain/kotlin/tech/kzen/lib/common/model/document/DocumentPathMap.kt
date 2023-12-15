package tech.kzen.lib.common.model.document

import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class DocumentPathMap<T>(
    val map: PersistentMap<DocumentPath, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    operator fun contains(documentPath: DocumentPath): Boolean {
        return map.containsKey(documentPath)
    }


    operator fun get(documentPath: DocumentPath): T? {
        return map[documentPath]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(documentPath: DocumentPath, instance: T): DocumentPathMap<T> {
        return DocumentPathMap(
                map.put(documentPath, instance))
    }


    fun remove(documentPath: DocumentPath): DocumentPathMap<T> {
        return DocumentPathMap(
                map.remove(documentPath))
    }


    fun filter(allowed: Set<DocumentNesting>): DocumentPathMap<T> {
        return DocumentPathMap(map
                .filter { e -> allowed.any(e.key::startsWith) }
                .toPersistentMap())
    }


    fun contains(documentNesting: DocumentNesting): Boolean {
        return map.any { e ->
            e.key.startsWith(documentNesting)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return map.toString()
    }
}