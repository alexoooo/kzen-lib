package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


// TODO: make less fragile
class GradleNotationSource(
        private val fileNotationSource: FileNotationSource
) : NotationMedia {

    //-----------------------------------------------------------------------------------------------------------------
    private val moduleRoot: String by lazy { moduleRootImpl() }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: ProjectPath): ByteArray {
        val candidates = candidateLocations(location)
        for (candidate in candidates) {
            if (Files.exists(candidate)) {
                return fileNotationSource.read(ProjectPath(candidate.toString()))
            }
        }

        throw IllegalStateException("Unknown resource: ${location.relativeLocation}")
    }



    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        fileNotationSource.write(location, bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun moduleRootImpl(): String =
        if (Files.exists(Paths.get("src"))) {
            "."
        }
        else {
            Files.list(Paths.get(".")).use {
                val jvmModule = it.filter({ it.fileName.endsWith("-jvm")}).findAny()

                if (! jvmModule.isPresent) {
                    throw IllegalStateException("No resources")
                }

                "${jvmModule.get()}"
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