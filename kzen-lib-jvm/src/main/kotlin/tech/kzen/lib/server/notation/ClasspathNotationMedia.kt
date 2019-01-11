package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.util.Digest


class ClasspathNotationMedia(
        private val prefix: String = NotationConventions.prefix,
        private val suffix: String = NotationConventions.suffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
) : NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<BundlePath, Digest> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): Map<BundlePath, Digest> {
        if (cache.isEmpty()) {
            val paths = scanPaths()

            for (path in paths) {
                val bytes = read(path)
                val digest = Digest.ofXoShiRo256StarStar(bytes)
                cache[path] = digest
            }
        }
        return cache
    }


    private fun scanPaths(): List<BundlePath> {
        return ClassPath
                .from(loader)
                .resources
                .filter {
                    it.resourceName.startsWith(prefix) &&
                            it.resourceName.endsWith(suffix) &&
                            BundlePath.matches(it.resourceName)
                }
                .map{ BundlePath.parse(it.resourceName) }
                .toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: BundlePath): ByteArray {
        val bytes = loader.getResource(location.asRelativeFile()).readBytes()
        println("ClasspathNotationMedia - read ${bytes.size}")
        return bytes
    }


    override suspend fun write(location: BundlePath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun delete(location: BundlePath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }
}