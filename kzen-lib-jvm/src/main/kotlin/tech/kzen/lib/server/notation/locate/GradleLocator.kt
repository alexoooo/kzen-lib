package tech.kzen.lib.server.notation.locate

import tech.kzen.lib.common.notation.model.ProjectPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


class GradleLocator: FileNotationLocator {
    //-----------------------------------------------------------------------------------------------------------------
    private val moduleRoot: String by lazy { moduleRootImpl() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun primaryRoot(): Path {
        return Paths.get("$moduleRoot/src/main/resources/")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun locateExisting(location: ProjectPath): Path {
        val candidates = candidateLocations(location)
        for (candidate in candidates) {
            println("GradleLocator - candidate: ${candidate.toAbsolutePath()}")

            if (Files.exists(candidate)) {
                return candidate
            }
        }

        throw IllegalStateException("Not found: ${location.relativeLocation}")
    }


    //-----------------------------------------------------------------------------------------------------------------
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
}