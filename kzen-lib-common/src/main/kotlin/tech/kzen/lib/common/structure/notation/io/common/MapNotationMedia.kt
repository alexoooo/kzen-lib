package tech.kzen.lib.common.structure.notation.io.common

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.resource.ResourceListing
import tech.kzen.lib.common.model.resource.ResourcePath
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.structure.notation.io.model.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class MapNotationMedia: NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val data = mutableMapOf<DocumentPath, MapDocumentMedia>()


    private class MapDocumentMedia(
            var document: ByteArray,
            var resources: MutableMap<ResourcePath, ByteArray>?
    )


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        val documentScans = data.mapValues {
            DocumentScan(
                    Digest.ofBytes(it.value.document),
                    it.value.resources?.let { resources ->
                        ResourceListing(
                                resources.mapValues { e ->
                                    Digest.ofBytes(e.value)
                                }.toPersistentMap())
                    }
            )
        }

        return NotationScan(DocumentPathMap(
                documentScans.toPersistentMap()
        ))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): ByteArray {
        return data[documentPath]!!.document
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: ByteArray) {
        val documentMedia = data.getOrPut(documentPath) {
            val resources =
                    if (documentPath.directory) {
                        mutableMapOf<ResourcePath, ByteArray>()
                    }
                    else {
                        null
                    }

            MapDocumentMedia(byteArrayOf(), resources)
        }

        documentMedia.document = contents
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        data.remove(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        val documentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        return documentData.resources?.get(resourceLocation.resourcePath)
                ?: throw IllegalArgumentException("Not found: $resourceLocation")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        val documentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentData.resources
                ?: throw IllegalArgumentException("No resources: $resourceLocation")

        resources[resourceLocation.resourcePath] = contents
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        val documentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentData.resources
                ?: throw IllegalArgumentException("No resources: $resourceLocation")

        check(resourceLocation.resourcePath in resources) {
            "Resource missing: $resourceLocation"
        }

        resources.remove(resourceLocation.resourcePath)
    }
}