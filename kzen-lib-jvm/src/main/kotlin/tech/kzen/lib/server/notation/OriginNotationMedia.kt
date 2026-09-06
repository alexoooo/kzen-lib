package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.collect.toPersistentMap
import java.net.URL
import java.net.URLClassLoader


/**
 * Bundled notation of exactly one origin: the given jar (or directory) URLs and nothing else. [ClasspathNotationMedia]
 * scans a class loader, which through Guava `ClassPath` includes every ancestor loader's resources and reads
 * parent-first, so it cannot say which of several plugin scopes a document came from and would serve a parent's
 * body under a child's name. This media scans a throwaway null-parent loader over [urls] only and retains each
 * document's exact resource [URL], reading through that handle, never through a logical-path lookup.
 * Documents are read eagerly at scan time: the origin is immutable and small.
 */
class OriginNotationMedia(
    private val urls: List<URL>,
    private val prefix: String = NotationConventions.documentPathPrefix,
    private val suffix: String = NotationConventions.fileDocumentSuffix,
    private val exclude: List<DocumentNesting> = listOf()
):
    NotationMedia
{
    //-----------------------------------------------------------------------------------------------------------------
    private val origins: Map<DocumentPath, URL>
    private val bodies: Map<DocumentPath, String>
    private val scan: NotationScan


    init {
        val found = linkedMapOf<DocumentPath, URL>()
        // One loader per URL: Guava's ClassPath collapses same-named resources of one loader to the first hit,
        // which would hide a document shipped by two jars of the same origin.
        for (url in urls) {
            URLClassLoader(arrayOf(url), null).use { isolated ->
                for (resource in ClassPath.from(isolated).resources) {
                    val name = resource.resourceName
                    if (!name.startsWith(prefix) || !name.endsWith(suffix) || !DocumentPath.matches(name)) {
                        continue
                    }
                    val documentPath = DocumentPath.parse(name.substring(prefix.length))
                    if (exclude.any { documentPath.startsWith(it) }) {
                        continue
                    }
                    val previous = found.put(documentPath, resource.url())
                    require(previous == null) {
                        "Document $documentPath appears twice within one origin: $previous and ${resource.url()}"
                    }
                }
            }
        }
        origins = found
        bodies = found.mapValues { (_, url) -> url.readText() }
        scan = NotationScan(DocumentPathMap(bodies.mapValues { (_, body) ->
            DocumentScan(Digest.ofUtf8(body), null)
        }.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The exact resource URL each document was read from. */
    fun origins(): Map<DocumentPath, URL> {
        return origins
    }


    override fun isReadOnly(): Boolean {
        return true
    }


    override suspend fun scan(): NotationScan {
        return scan
    }


    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        return bodies[documentPath]
            ?: throw IllegalArgumentException("Not found in this origin: $documentPath")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun containsResource(resourceLocation: ResourceLocation): Boolean {
        return false
    }

    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        throw UnsupportedOperationException("Origin media carries no resources: $resourceLocation")
    }

    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        throw UnsupportedOperationException("Origin media is read-only")
    }

    override suspend fun deleteDocument(documentPath: DocumentPath) {
        throw UnsupportedOperationException("Origin media is read-only")
    }

    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        throw UnsupportedOperationException("Origin media is read-only")
    }

    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        throw UnsupportedOperationException("Origin media is read-only")
    }

    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        throw UnsupportedOperationException("Origin media is read-only")
    }


    override fun invalidate() {}
}
