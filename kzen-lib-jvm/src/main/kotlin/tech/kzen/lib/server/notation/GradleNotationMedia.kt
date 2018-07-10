package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


// TODO: make less fragile
class GradleNotationMedia(
        private val fileNotationMedia: FileNotationMedia
) : NotationMedia {

    //-----------------------------------------------------------------------------------------------------------------
    private val moduleRoot: String by lazy { moduleRootImpl() }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: ProjectPath): ByteArray {
        println("GradleNotationMedia | read - moduleRoot: $moduleRoot")

        val candidates = candidateLocations(location)
        for (candidate in candidates) {
            println("GradleNotationMedia - candidate: ${candidate.toAbsolutePath()}")

            if (Files.exists(candidate)) {
                return fileNotationMedia.read(ProjectPath(candidate.toString()))
            }
        }

        throw IllegalStateException("Unknown resource: ${location.relativeLocation}")
    }



    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
//        fileNotationMedia.write(location, bytes)

        println("GradleNotationMedia | write - moduleRoot: $moduleRoot | ${bytes.size}")

        val candidates = candidateLocations(location)
        for (candidate in candidates) {
            println("GradleNotationMedia - candidate: ${candidate.toAbsolutePath()}")

            if (Files.exists(candidate)) {
                fileNotationMedia.write(ProjectPath(candidate.toString()), bytes)
                return
            }
        }

        throw IllegalStateException("Unknown resource: ${location.relativeLocation}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun moduleRootImpl(): String =
        if (Files.exists(Paths.get("src"))) {
            "."
        }
        else {
            Files.list(Paths.get(".")).use { files ->
                val list = files.collect(Collectors.toList())

                val jvmModule = list.firstOrNull({ it.fileName.toString().endsWith("-jvm")})
                        ?: throw IllegalStateException("No resources: - $list")

                "$jvmModule"
            }
        }


    private fun candidateLocations(location: ProjectPath): List<Path> {
        return listOf(
                mainPath(location),
                testPath(location))
    }


    private fun mainPath(location: ProjectPath): Path {
        return Paths.get(
                "$moduleRoot/src/main/resources/${location.relativeLocation}").normalize()
    }


    private fun testPath(location: ProjectPath): Path {
        return Paths.get(
                "$moduleRoot/src/test/resources/${location.relativeLocation}").normalize()
    }
}