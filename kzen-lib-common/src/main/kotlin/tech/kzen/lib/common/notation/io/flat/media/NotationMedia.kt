package tech.kzen.lib.common.notation.io.flat.media

import tech.kzen.lib.common.notation.model.ProjectPath


interface NotationMedia {
    suspend fun read(location: ProjectPath): ByteArray
    suspend fun write(location: ProjectPath, bytes: ByteArray)
}