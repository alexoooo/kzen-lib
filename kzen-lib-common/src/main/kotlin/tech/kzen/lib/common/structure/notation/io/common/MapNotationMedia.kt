package tech.kzen.lib.common.structure.notation.io.common

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.model.resource.ResourceInfo
import tech.kzen.lib.common.model.resource.ResourceListing
import tech.kzen.lib.common.model.resource.ResourcePath
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.structure.notation.io.model.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class MapNotationMedia: NotationMedia {
    private val data = mutableMapOf<DocumentPath, MapDocumentMedia>()


    private class MapDocumentMedia(
            var document: ByteArray,
            var resources: MutableMap<ResourcePath, ByteArray>?
    )


    override suspend fun scan(): NotationScan {
        val documentScans = data.mapValues {
            DocumentScan(
                    Digest.ofXoShiRo256StarStar(it.value.document),
                    it.value.resources?.let { resources ->
                        ResourceListing(
                                resources.mapValues { e ->
                                    ResourceInfo(
                                            e.value.size,
                                            Digest.ofXoShiRo256StarStar(e.value)
                                    )
                                })
                    }
            )
        }

        return NotationScan(DocumentPathMap(
                documentScans.toPersistentMap()
        ))
    }


    override suspend fun read(location: DocumentPath): ByteArray {
        return data[location]!!.document
    }


    override suspend fun write(location: DocumentPath, bytes: ByteArray) {
        val documentMedia = data.getOrPut(location) {
            MapDocumentMedia(byteArrayOf(), null)
        }

        documentMedia.document = bytes
    }


    override suspend fun delete(location: DocumentPath) {
        data.remove(location)
    }
}