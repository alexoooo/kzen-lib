package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.toPersistentMap
import java.util.concurrent.ConcurrentHashMap


@Suppress("unused")
class ClasspathNotationMedia(
    private val prefix: String = NotationConventions.documentPathPrefix,
    private val suffix: String = NotationConventions.fileDocumentSuffix,
    private val exclude: List<DocumentNesting> = listOf(),
    private val loader: ClassLoader = Thread.currentThread().contextClassLoader
):
    NotationMedia
{
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache: NotationScan? = null
    private var documentCache: MutableMap<DocumentPath, String> = ConcurrentHashMap()


    //-----------------------------------------------------------------------------------------------------------------
    override fun isReadOnly(): Boolean {
        return true
    }


    override suspend fun scan(): NotationScan {
        if (scanCache != null) {
            return scanCache!!
        }

        val paths = scanPaths()

        val builder = mutableMapOf<DocumentPath, DocumentScan>()

        for (path in paths) {
            val body = readDocument(path, null)
            val digest = Digest.ofUtf8(body)
            builder[path] = DocumentScan(
                digest,
                null)
        }

        scanCache = NotationScan(
            DocumentPathMap(builder.toPersistentMap()))

        return scanCache!!
    }


    private fun scanPaths(): List<DocumentPath> {
        return ClassPath
            .from(loader)
            .resources
            .filter {
                it.resourceName.startsWith(prefix) &&
                        it.resourceName.endsWith(suffix) &&
                        DocumentPath.matches(it.resourceName)
            }
            .map {
                DocumentPath.parse(it.resourceName.substring(prefix.length))
            }
            .filter { documentPath ->
                exclude.none { documentPath.startsWith(it) }
            }
            .toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        return documentCache.computeIfAbsent(documentPath) {
            val resourcePath =
                if (prefix.endsWith('/')) {
                    prefix + documentPath.asRelativeFile()
                }
                else {
                    prefix + "/" + documentPath.asRelativeFile()
                }

            val body = loader.getResource(resourcePath)
            require(body != null) { "Classpath resource not found: $resourcePath" }

            body.readText()
        }
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun containsResource(resourceLocation: ResourceLocation): Boolean {
        return false
    }


    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        TODO("not implemented")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        throw UnsupportedOperationException("Classpath copying not supported")
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        // NB: NOOP because classpath resources can't change
    }
}