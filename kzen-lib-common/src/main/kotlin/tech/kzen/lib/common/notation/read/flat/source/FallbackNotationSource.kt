package tech.kzen.lib.common.notation.read.flat.source

import tech.kzen.lib.common.notation.model.ProjectPath


class FallbackNotationSource(
        private val sources: List<NotationSource>
) : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        for (source in sources) {
            try {
                return source.read(location)
            }
            catch (ignored: Exception) {}
        }

        throw IllegalArgumentException("Unable to read: $location")
    }
}