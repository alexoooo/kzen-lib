package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.toPersistentMap


class MapNotationMedia: NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val data = mutableMapOf<DocumentPath, MapDocumentMedia>()


    private class MapDocumentMedia(
            var document: String,
            var resources: MutableMap<ResourcePath, ImmutableByteArray>?
    )


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        val documentScans = data.mapValues {
            DocumentScan(
                    Digest.ofUtf8(it.value.document),
                    it.value.resources?.let { resources ->
                        ResourceListing(
                                resources.mapValues { e ->
                                    e.value.digest()
                                }.toPersistentMap())
                    }
            )
        }

        return NotationScan(DocumentPathMap(
                documentScans.toPersistentMap()
        ))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): String {
        return data[documentPath]!!.document
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        val documentMedia = data.getOrPut(documentPath) {
            val resources =
                    if (documentPath.directory) {
                        mutableMapOf<ResourcePath, ImmutableByteArray>()
                    }
                    else {
                        null
                    }

            MapDocumentMedia("", resources)
        }

        documentMedia.document = contents
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        data.remove(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        val documentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        return documentData.resources?.get(resourceLocation.resourcePath)
                ?: throw IllegalArgumentException("Not found: $resourceLocation")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        val documentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentData.resources
                ?: throw IllegalArgumentException("No resources: $resourceLocation")

        resources[resourceLocation.resourcePath] = contents
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        val sourceDocumentData = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val sourceResources = sourceDocumentData.resources
                ?: throw IllegalArgumentException("No resources: $resourceLocation")

        val sourceContents = sourceResources[resourceLocation.resourcePath]
                ?: throw IllegalArgumentException("Not found: $resourceLocation")

        val destinationDocumentData = data[destination.documentPath]
                ?: throw IllegalArgumentException("Not found: ${destination.documentPath}")

        val destinationResources = destinationDocumentData.resources
                ?: throw IllegalArgumentException("No resources: $destination")

        destinationResources[destination.resourcePath] = sourceContents
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        // NB: NOOP because map notation is a source of truth all its own
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun isEmpty(): Boolean {
//        return data.isEmpty()
//    }
//
//
//    fun clear() {
//        data.clear()
//    }
}