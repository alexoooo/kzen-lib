package tech.kzen.lib.common.structure.notation.io.common

import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.DocumentTree
import tech.kzen.lib.common.util.Digest


class MapNotationMedia: NotationMedia {
    private val data = mutableMapOf<DocumentPath, ByteArray>()


    override suspend fun scan(): DocumentTree<Digest> {
        val digests = data.mapValues { Digest.ofXoShiRo256StarStar(it.value) }
        return DocumentTree(digests)
    }


    override suspend fun read(location: DocumentPath): ByteArray {
        return data[location]!!
    }


    override suspend fun write(location: DocumentPath, bytes: ByteArray) {
        data[location] = bytes
    }


    override suspend fun delete(location: DocumentPath) {
        data.remove(location)
    }
}