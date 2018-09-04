package tech.kzen.lib.server.notation

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


class ClasspathNotationMedia(
        private val prefix: String = NotationConventions.prefix,
        private val suffix: String = NotationConventions.suffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
) : NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<ProjectPath, Digest> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): Map<ProjectPath, Digest> {
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


    private fun scanPaths(): List<ProjectPath> {
        return ClassPath
                .from(loader)
                .resources
                .filter {
                    it.resourceName.startsWith(prefix) &&
                            it.resourceName.endsWith(suffix) &&
                            ProjectPath.matches(it.resourceName)
                }
                .map{ ProjectPath(it.resourceName) }
                .toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: ProjectPath): ByteArray {
        val bytes = loader.getResource(location.relativeLocation).readBytes()
        println("ClasspathNotationMedia - read ${bytes.size}")
        return bytes
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun delete(location: ProjectPath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }
}