package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.util.Digest


interface NotationMedia {
//    suspend fun digest(location: ProjectPath): Digest {
//        val bytes = read(location)
//        return Digest.ofXoShiRo256StarStar(bytes)
//    }

    suspend fun scan(): Map<BundlePath, Digest>

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