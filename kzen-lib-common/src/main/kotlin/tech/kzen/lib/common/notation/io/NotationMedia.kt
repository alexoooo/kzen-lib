package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


interface NotationMedia {
//    suspend fun digest(location: ProjectPath): Digest {
//        val bytes = read(location)
//        return Digest.ofXoShiRo256StarStar(bytes)
//    }

    suspend fun scan(): Map<ProjectPath, Digest>

    /**
     * Must exist
     */
    suspend fun read(location: ProjectPath): ByteArray


    /**
     * Create if not exists
     */
    suspend fun write(location: ProjectPath, bytes: ByteArray)


    suspend fun delete(location: ProjectPath)
}