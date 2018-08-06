package tech.kzen.lib.common.notation.io.common

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest


class MultiNotationMedia(
        private val media: List<NotationMedia>
) : NotationMedia {
    override suspend fun scan(): Map<ProjectPath, Digest> {
        val all = mutableMapOf<ProjectPath, Digest>()
        for (delegate in media) {
            all.putAll(delegate.scan())
        }
        return all
    }


    override suspend fun read(location: ProjectPath): ByteArray {
        for (source in media) {
            try {
                return source.read(location)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Not found in $source - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $location")
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        for (medium in media) {
            try {
                return medium.write(location, bytes)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $location")
    }


    override suspend fun delete(location: ProjectPath) {
        for (medium in media) {
            try {
                return medium.delete(location)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't delete in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $location")
    }
}