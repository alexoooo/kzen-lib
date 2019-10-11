package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.ImmutableByteArray


interface NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scan(): NotationScan


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Must exist
     */
    suspend fun readDocument(documentPath: DocumentPath): String


    /**
     * Create if not exists
     */
    suspend fun writeDocument(documentPath: DocumentPath, contents: String)


    /**
     * Deletes document notation, and any associated resources
     */
    suspend fun deleteDocument(documentPath: DocumentPath)


    //-----------------------------------------------------------------------------------------------------------------
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