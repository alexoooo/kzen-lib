package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import java.nio.file.Files
import java.nio.file.Paths


class FileNotationSource : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
        val path = Paths.get(location.relativeLocation)
        return Files.readAllBytes(path)
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        val path = Paths.get(location.relativeLocation)
        Files.write(path, bytes)
    }
}