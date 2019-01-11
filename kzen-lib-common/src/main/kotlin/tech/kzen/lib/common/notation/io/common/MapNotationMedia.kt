package tech.kzen.lib.common.notation.io.common

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.util.Digest


class MapNotationMedia: NotationMedia {
    private val data = mutableMapOf<BundlePath, ByteArray>()


    override suspend fun scan(): Map<BundlePath, Digest> {
        return data.mapValues { Digest.ofXoShiRo256StarStar(it.value) }
    }


    override suspend fun read(location: BundlePath): ByteArray {
        return data[location]!!
    }


    override suspend fun write(location: BundlePath, bytes: ByteArray) {
        data[location] = bytes
    }


    override suspend fun delete(location: BundlePath) {
        data.remove(location)
    }
}