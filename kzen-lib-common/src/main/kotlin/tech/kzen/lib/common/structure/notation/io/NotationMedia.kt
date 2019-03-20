package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.DocumentTree
import tech.kzen.lib.common.util.Digest


interface NotationMedia {
    suspend fun scan(): DocumentTree<Digest>

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