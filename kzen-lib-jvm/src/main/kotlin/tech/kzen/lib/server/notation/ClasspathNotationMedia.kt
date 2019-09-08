package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.structure.notation.io.model.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class ClasspathNotationMedia(
        private val prefix: String = NotationConventions.documentPathPrefix,
        private val suffix: String = NotationConventions.fileDocumentSuffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<DocumentPath, DocumentScan> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        if (cache.isEmpty()) {
            val paths = scanPaths()

            for (path in paths) {
                val bytes = readDocument(path)
                val digest = Digest.ofBytes(bytes)
                cache[path] = DocumentScan(
                        digest,
                        null)
            }
        }
        return NotationScan(
                DocumentPathMap(cache.toPersistentMap()))
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
                .map{
                    DocumentPath.parse(
                            it.resourceName.substring(prefix.length)
                    )
                }
                .toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): ByteArray {
        val resourcePath = prefix + "/" + documentPath.asRelativeFile()

        @Suppress("UnnecessaryVariable")
        val bytes = loader.getResource(resourcePath).readBytes()

//        println("ClasspathNotationMedia - read ${bytes.size}")
        return bytes
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun deleteDocument(documentPath: DocumentPath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        TODO("not implemented")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }
}