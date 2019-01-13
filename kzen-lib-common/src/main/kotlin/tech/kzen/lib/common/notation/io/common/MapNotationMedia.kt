package tech.kzen.lib.common.notation.io.common

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.util.Digest


class MapNotationMedia: NotationMedia {
    private val data = mutableMapOf<BundlePath, ByteArray>()


    override suspend fun scan(): BundleTree<Digest> {
        val digests = data.mapValues { Digest.ofXoShiRo256StarStar(it.value) }
        return BundleTree(digests)
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