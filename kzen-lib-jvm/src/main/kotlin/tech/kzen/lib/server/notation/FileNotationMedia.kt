package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import java.nio.file.Files
import java.nio.file.Paths


class FileNotationMedia : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
        val path = Paths.get(location.relativeLocation)

        println("FileNotationMedia | read - path: ${path.toAbsolutePath()}")

        return Files.readAllBytes(path)
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        val path = Paths.get(location.relativeLocation)

        println("FileNotationMedia | write - path: ${path.toAbsolutePath()}")

        Files.write(path, bytes)
    }
}