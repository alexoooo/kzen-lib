package tech.kzen.lib.common.structure.notation.io.common

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class MapNotationMedia: NotationMedia {
    private val data = mutableMapOf<DocumentPath, ByteArray>()


    override suspend fun scan(): DocumentPathMap<Digest> {
        val digests = data.mapValues { Digest.ofXoShiRo256StarStar(it.value) }
        return DocumentPathMap(digests.toPersistentMap())
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