package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class SeededNotationMedia(
        private val underlying: NotationMedia
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
//    private val delegate = MapNotationMedia()
    private val data = mutableMapOf<DocumentPath, SeededDocumentMedia>()
    private var notationScanCache: NotationScan? = null


    private class SeededDocumentMedia(
            var document: String,
//            var resources: MutableMap<ResourcePath, ByteArray>?
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

        val documentMedia = data.getOrPut(documentPath) {
            val resources =
                    if (documentPath.directory) {
                        mutableMapOf<ResourcePath, Digest>()
                    }
                    else {
                        null
                    }

            SeededDocumentMedia("", resources)
        }

        documentMedia.document = contents
        notationScanCache = null
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        seedIfRequired()

        require(documentPath in data) {
            "Not found: $documentPath"
        }

        data.remove(documentPath)
        notationScanCache = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        seedIfRequired()
        return underlying.readResource(resourceLocation)
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        seedIfRequired()

        val documentMedia = data[resourceLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resources = documentMedia.resources
                ?: throw IllegalArgumentException("Directory document expected: ${resourceLocation.documentPath}")

        resources[resourceLocation.resourcePath] = Digest.ofBytes(contents)
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
            val document = readDocument(e.key)

            val resources =
                    seedResources(e.key, e.value)

            data[e.key] = SeededDocumentMedia(
                    document, resources)
        }
    }


    private fun seedResources(
            documentPath: DocumentPath,
            documentScan: DocumentScan
    ): MutableMap<ResourcePath, Digest>? {
        if (! documentPath.directory) {
            return null
        }
        return documentScan.resources!!.values.toMutableMap()
    }
}