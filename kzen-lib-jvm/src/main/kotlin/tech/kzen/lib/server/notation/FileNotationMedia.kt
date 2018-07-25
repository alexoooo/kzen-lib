package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import java.nio.file.Files


class FileNotationMedia(
        private val notationLocator: FileNotationLocator
) : NotationMedia {

    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: ProjectPath): ByteArray {
        val path = notationLocator.locateExisting(location)

        println("GradleNotationMedia | read - moduleRoot: ${path.toAbsolutePath().normalize()}")

        return Files.readAllBytes(path)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        val path = notationLocator.locateExisting(location)

        println("GradleNotationMedia | write - moduleRoot: ${path.toAbsolutePath().normalize()} | ${bytes.size}")

        Files.write(path, bytes)
    }
}