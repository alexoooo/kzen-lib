package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.DocumentTree
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest


class ClasspathNotationMedia(
        private val prefix: String = NotationConventions.prefix,
        private val suffix: String = NotationConventions.suffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<DocumentPath, Digest> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): DocumentTree<Digest> {
        if (cache.isEmpty()) {
            val paths = scanPaths()

            for (path in paths) {
                val bytes = read(path)
                val digest = Digest.ofXoShiRo256StarStar(bytes)
                cache[path] = digest
            }
        }
        return DocumentTree(cache)
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
    override suspend fun read(location: DocumentPath): ByteArray {
        val resourcePath = prefix + "/" + location.asRelativeFile()
        val bytes = loader.getResource(resourcePath).readBytes()
//        println("ClasspathNotationMedia - read ${bytes.size}")
        return bytes
    }


    override suspend fun write(location: DocumentPath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun delete(location: DocumentPath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }
}