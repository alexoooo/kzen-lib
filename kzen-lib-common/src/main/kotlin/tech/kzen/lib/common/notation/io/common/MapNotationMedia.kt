package tech.kzen.lib.common.notation.io.common

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


class MapNotationMedia : NotationMedia {
    private val data = mutableMapOf<ProjectPath, ByteArray>()


    override suspend fun scan(): Map<ProjectPath, Digest> {
        return data.mapValues { Digest.ofXoShiRo256StarStar(it.value) }
    }


    override suspend fun read(location: ProjectPath): ByteArray {
        return data[location]!!
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        data[location] = bytes
    }


    override suspend fun delete(location: ProjectPath) {
        data.remove(location)
    }
}