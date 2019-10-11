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


class SeededNotationMedia(
        private val underlying: NotationMedia
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val data = mutableMapOf<DocumentPath, SeededDocumentMedia>()
    private var notationScanCache: NotationScan? = null


    private class SeededDocumentMedia(
            var document: String,
            var resources: MutableMap<ResourcePath, Digest>?
    )


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        if (notationScanCache == null) {
            notationScanCache = scanImpl()
        }
        return notationScanCache!!
    }


    private suspend fun scanImpl(): NotationScan {
        seedIfRequired()

        val documents = mutableMapOf<DocumentPath, DocumentScan>()

        for (e in data) {
            documents[e.key] = DocumentScan(
                    Digest.ofUtf8(e.value.document),
                    e.value.resources?.let {
                        ResourceListing(it.toPersistentMap())
                    }
            )
        }

        return NotationScan(DocumentPathMap(
                documents.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): String {
        seedIfRequired()

        val documentMedia = data[documentPath]
                ?: throw IllegalArgumentException("Not found: $documentPath")

        return documentMedia.document
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        seedIfRequired()

        val documentMedia = getOrInitDocumentMedia(documentPath)

        documentMedia.document = contents
        notationScanCache = null
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        seedIfRequired()

        val sourceDocumentMedia = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val sourceResources = sourceDocumentMedia.resources
                ?: throw IllegalArgumentException("No resources: $resourceLocation")

        val sourceResourceDigest = sourceResources[resourceLocation.resourcePath]
                ?: throw IllegalArgumentException("Resource not found: $resourceLocation")

        val destinationDocumentMedia = getOrInitDocumentMedia(destination.documentPath)

        val destinationResources = destinationDocumentMedia.resources
                ?: throw IllegalArgumentException("No resources: $destination")

        destinationResources[destination.resourcePath] = sourceResourceDigest
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        seedIfRequired()

        require(documentPath in data) {
            "Not found: $documentPath"
        }

        data.remove(documentPath)
        notationScanCache = null
    }


    private fun getOrInitDocumentMedia(documentPath: DocumentPath): SeededDocumentMedia {
        return data.getOrPut(documentPath) {
            val resources =
                    if (documentPath.directory) {
                        mutableMapOf<ResourcePath, Digest>()
                    }
                    else {
                        null
                    }

            SeededDocumentMedia("", resources)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        seedIfRequired()
        return underlying.readResource(resourceLocation)
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        seedIfRequired()

        val documentMedia = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentMedia.resources
                ?: throw IllegalArgumentException("Directory document expected: ${resourceLocation.documentPath}")

        resources[resourceLocation.resourcePath] = contents.digest()
        notationScanCache = null
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        seedIfRequired()

        val documentMedia = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentMedia.resources
                ?: throw IllegalArgumentException("Directory document expected: ${resourceLocation.documentPath}")

        require(resourceLocation.resourcePath in resources) {
            "Resource not found: $resourceLocation"
        }

        resources.remove(resourceLocation.resourcePath)
        notationScanCache = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        data.clear()
        notationScanCache = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun seedIfRequired() {
        if (data.isNotEmpty()) {
            return
        }

        val scan = underlying.scan()

        for (e in scan.documents.values) {
            val document = underlying.readDocument(e.key)

            val resources =
                    if (e.key.directory) {
                        e.value.resources!!.digests.toMutableMap()
                    }
                    else {
                        null
                    }

            data[e.key] = SeededDocumentMedia(
                    document, resources)
        }
    }
}