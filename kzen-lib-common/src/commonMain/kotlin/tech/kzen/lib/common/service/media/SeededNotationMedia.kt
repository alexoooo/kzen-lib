package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
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
    override fun isReadOnly(): Boolean {
        return false
    }


    override suspend fun scan(): NotationScan {
        if (notationScanCache == null) {
            notationScanCache = scanImpl()
        }
        return notationScanCache!!
    }


    private suspend fun scanImpl(): NotationScan {
        seedIfRequired()

        // Mirror FileNotationMedia: a folder entry is only surfaced when its directory holds no documents
        // (otherwise the folder is implied by its nested documents). The explicit entry stays in `data`, but
        // suppressing it here keeps this client scan in agreement with the server's — so MirroredGraphStore
        // doesn't see a phantom mismatch and refresh the folder away, and the folder resurfaces here as soon
        // as its last document is deleted.
        val documentNestings = data.keys
            .filter { !it.folder }
            .map { it.nesting }

        val documents = mutableMapOf<DocumentPath, DocumentScan>()

        for (e in data) {
            if (e.key.folder) {
                val contentNesting = e.key.nesting.plus(DocumentSegment(e.key.name.value))
                val hasNestedDocument = documentNestings.any { it.startsWith(contentNesting) }
                if (hasNestedDocument) {
                    continue
                }
            }

            val resources: ResourceListing? =
                e.value.resources?.let {
                    ResourceListing(it.toPersistentMap())
                }

            documents[e.key] = DocumentScan(
                    Digest.ofUtf8(e.value.document), resources)
        }

        return NotationScan(DocumentPathMap(
                documents.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
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


    override suspend fun createFolder(documentPath: DocumentPath) {
        seedIfRequired()

        // a folder has no body and no resources; its DocumentPath form (Folder) is the discriminator
        data.getOrPut(documentPath) { SeededDocumentMedia("", null) }
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

        // folders have no body to read — skip them in the batch read, seed them as empty markers below
        val documentPaths = scan.documents.map.keys.filter { !it.folder }
        val documents = underlying.readDocuments(documentPaths)

        for (e in scan.documents.map) {
            if (e.key.folder) {
                data[e.key] = SeededDocumentMedia("", null)
                continue
            }

            val document = documents[e.key]
                ?: throw IllegalStateException("Missing document: ${e.key}")

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