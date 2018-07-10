package tech.kzen.lib.common.notation.io.flat.media

import tech.kzen.lib.common.notation.model.ProjectPath


class FallbackNotationMedia(
        private val media: List<NotationMedia>
) : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
        for (source in media) {
            try {
                return source.read(location)
            }
            catch (ignored: Exception) {
                println("FallbackNotationMedia - Not found in $source - ${ignored.message}")
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
                println("FallbackNotationMedia - Can't write in $medium - ${ignored.message}")}
        }

        throw IllegalArgumentException("Unable to write: $location")
    }
}