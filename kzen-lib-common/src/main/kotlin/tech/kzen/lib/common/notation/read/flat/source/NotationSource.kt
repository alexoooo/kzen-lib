package tech.kzen.lib.common.notation.read.flat.source

import tech.kzen.lib.common.notation.model.ProjectPath


interface NotationSource {
    suspend fun read(location: ProjectPath): ByteArray
}