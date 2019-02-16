package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.util.Digest


interface NotationMedia {
    suspend fun scan(): BundleTree<Digest>

    /**
     * Must exist
     */
    suspend fun read(location: BundlePath): ByteArray


    /**
     * Create if not exists
     */
    suspend fun write(location: BundlePath, bytes: ByteArray)


    suspend fun delete(location: BundlePath)
}