package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.resource.ResourcePath
import tech.kzen.lib.common.structure.notation.io.model.NotationScan


interface NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun scan(): NotationScan


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Must exist
     */
    suspend fun readDocument(documentPath: DocumentPath): ByteArray


    /**
     * Create if not exists
     */
    suspend fun writeDocument(documentPath: DocumentPath, contents: ByteArray)


    /**
     * Deletes document notation, and any associated resources
     */
    suspend fun deleteDocument(documentPath: DocumentPath)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun readResource(resourceLocation: ResourceLocation): ByteArray


    suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray)


    suspend fun deleteResource(resourceLocation: ResourceLocation)
}