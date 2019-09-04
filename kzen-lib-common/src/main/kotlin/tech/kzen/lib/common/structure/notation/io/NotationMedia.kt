package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.notation.io.model.NotationScan


interface NotationMedia {
    suspend fun scan(): NotationScan

    /**
     * Must exist
     */
    suspend fun read(location: DocumentPath): ByteArray


    /**
     * Create if not exists
     */
    suspend fun write(location: DocumentPath, bytes: ByteArray)


    suspend fun delete(location: DocumentPath)
}