package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest


interface NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    fun isReadOnly(): Boolean

    suspend fun scan(): NotationScan


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun containsDocument(documentPath: DocumentPath): Boolean {
        return scan().documents.contains(documentPath)
    }


    /**
     * Must exist
     */
    suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest? = null): String


    /**
     * Bulk read; default impl loops [readDocument]. Implementations that can
     * fetch many documents in a single round trip (e.g. REST clients) should
     * override.
     */
    suspend fun readDocuments(paths: Collection<DocumentPath>): Map<DocumentPath, String> {
        return paths.associateWith { readDocument(it) }
    }


    /**
     * Create if not exists
     */
    suspend fun writeDocument(documentPath: DocumentPath, contents: String)


    /**
     * Create a pure folder (a markerless directory that is NOT a document). documentPath.form must be Folder.
     * Default impls that don't support writing folders throw — only writable media (file / seeded / map) override.
     */
    suspend fun createFolder(documentPath: DocumentPath) {
        throw UnsupportedOperationException("Folders not supported here: $documentPath")
    }


    /**
     * Remove a folder directory and anything still in it. Tolerant of the folder being absent (it may already
     * have been removed entry-by-entry, or never had an entry because it contained documents). Default no-op for
     * media with no directory concept (e.g. the in-memory client seed, where entries are removed individually).
     */
    suspend fun deleteFolder(documentPath: DocumentPath) {}


    /**
     * Deletes document notation, and any associated resources
     */
    suspend fun deleteDocument(documentPath: DocumentPath)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun containsResource(resourceLocation: ResourceLocation): Boolean {
        val documentScan = scan().documents[resourceLocation.documentPath]
            ?: return false

        return documentScan.resources?.digests?.containsKey(resourceLocation.resourcePath) ?: false
    }


    suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray


    suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray)


    // NB: allows to bypass readResource on client in MirroredGraphStore
    suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation)


    suspend fun deleteResource(resourceLocation: ResourceLocation)


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Refresh any caches to reflect the source of truth
     */
    fun invalidate()
}