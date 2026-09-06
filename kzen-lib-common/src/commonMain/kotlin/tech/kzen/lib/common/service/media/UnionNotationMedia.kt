package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * Read-only union of several read-only media with disjoint document paths, each document served by the member
 * that scanned it. Members are scanned once at construction ([of]); a path present in two members is refused
 * there by name, so an overlap is a construction-time fault rather than a first-one-wins read.
 */
class UnionNotationMedia private constructor(
    private val members: List<NotationMedia>,
    private val ownerByPath: Map<DocumentPath, NotationMedia>,
    private val scan: NotationScan
):
    NotationMedia
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        suspend fun of(members: List<NotationMedia>): UnionNotationMedia {
            val owners = mutableMapOf<DocumentPath, NotationMedia>()
            val documents = mutableMapOf<DocumentPath, DocumentScan>()
            for (member in members) {
                require(member.isReadOnly()) { "Union members must be read-only" }
                val memberScan = member.scan()
                for ((path, documentScan) in memberScan.documents.map) {
                    val previous = owners.put(path, member)
                    require(previous == null) { "Document $path is served by two union members" }
                    documents[path] = documentScan
                }
            }
            return UnionNotationMedia(members, owners, NotationScan(DocumentPathMap(documents.toPersistentMap())))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun isReadOnly(): Boolean {
        return true
    }


    override suspend fun scan(): NotationScan {
        return scan
    }


    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        return owner(documentPath).readDocument(documentPath, expectedDigest)
    }


    override suspend fun containsResource(resourceLocation: ResourceLocation): Boolean {
        val owner = ownerByPath[resourceLocation.documentPath]
            ?: return false
        return owner.containsResource(resourceLocation)
    }


    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        return owner(resourceLocation.documentPath).readResource(resourceLocation)
    }


    override fun invalidate() {
        members.forEach { it.invalidate() }
    }


    private fun owner(documentPath: DocumentPath): NotationMedia {
        return ownerByPath[documentPath]
            ?: throw IllegalArgumentException("Not found: $documentPath")
    }


    //-----------------------------------------------------------------------------------------------------------------
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
