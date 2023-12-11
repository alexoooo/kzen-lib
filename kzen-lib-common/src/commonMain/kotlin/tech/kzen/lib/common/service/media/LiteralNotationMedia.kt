package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


@Suppress("unused")
class LiteralNotationMedia(
    private val documents: DocumentPathMap<Document>
):
    NotationMedia
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        suspend fun filter(base: NotationMedia, exclude: NotationMedia): LiteralNotationMedia {
            val excludeScan = exclude.scan()
            val baseScan = base.scan()

            val baseUnique = baseScan.documents.values.keys.filter {
                it !in excludeScan.documents.values.keys
            }

            val filtered = mutableMapOf<DocumentPath, Document>()
            for (documentPath in baseUnique) {
                val resourceListings = baseScan.documents[documentPath]!!.resources

                val resourceContents = resourceListings?.let { resourceListing ->
                    resourceListing.digests.mapValues {
                        base.readResource(ResourceLocation(documentPath, it.key))
                    }
                }

                filtered[documentPath] = Document(
                    base.readDocument(documentPath),
                    resourceContents
                )
            }

            return LiteralNotationMedia(
                DocumentPathMap(filtered.toPersistentMap()))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Document(
        val body: String,
        val resources: Map<ResourcePath, ImmutableByteArray>?
    )


    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache: NotationScan? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun isReadOnly(): Boolean {
        return true
    }


    override suspend fun scan(): NotationScan {
        scanCache?.let {
            return it
        }

        val documentScans = documents.values.mapValues { doc ->
            DocumentScan(
                Digest.ofUtf8(doc.value.body),
                doc.value.resources?.let { resource ->
                    ResourceListing(resource.mapValues {
                        it.value.digest()
                    }.toPersistentMap())
                }
            )
        }

        val scan = NotationScan(DocumentPathMap(
            documentScans.toPersistentMap()))
        scanCache = scan
        return scan
    }


    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        return documents[documentPath]?.body
            ?: throw IllegalArgumentException("Not found: $documentPath")
    }


    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        val resources = documents[resourceLocation.documentPath]?.resources
            ?: throw IllegalArgumentException(
                "Document not found: ${resourceLocation.documentPath} (${resourceLocation.resourcePath})")

        return resources[resourceLocation.resourcePath]
            ?: throw IllegalArgumentException("Resource not found: $resourceLocation")
    }


    override fun invalidate() {}



    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        throw UnsupportedOperationException("read-only")
    }

    override suspend fun deleteDocument(documentPath: DocumentPath) {
        throw UnsupportedOperationException("read-only")
    }

    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        throw UnsupportedOperationException("read-only")
    }

    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        throw UnsupportedOperationException("read-only")
    }

    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        throw UnsupportedOperationException("read-only")
    }
}