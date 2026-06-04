package tech.kzen.lib.server.notation.locate

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.service.notation.NotationConventions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


class GradleLocator(
    private var includeTest: Boolean = false,

    // Explicit module root (the directory containing src/main/resources/notation), bypassing
    //  the cwd heuristic below — for processes whose cwd is not the module they serve.
    private val moduleRootOverride: Path? = null
): FileNotationLocator {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val mainResourcesRelative = "src/main/resources/" + NotationConventions.documentPathPrefix
        private const val mainResources = "/$mainResourcesRelative"
        private const val testResources = "/src/test/resources/" + NotationConventions.documentPathPrefix


        /**
         * Module root (the directory containing src/main/resources/notation) of the module whose
         *  build produced the given class, walked up from its code source (classes dir in IDE /
         *  Gradle builds, jar under build/libs in distributions) — for processes whose working
         *  directory is not the module they serve (pass the result as moduleRootOverride).
         */
        fun moduleRootOfCodeSource(clazz: Class<*>): Path {
            val codeSource = clazz.protectionDomain.codeSource?.location
                ?: error("Code source unavailable: ${clazz.name}")

            var dir: Path? = Paths.get(codeSource.toURI()).parent
            while (dir != null) {
                if (Files.isDirectory(dir.resolve(mainResourcesRelative))) {
                    return dir
                }
                dir = dir.parent
            }

            error("Module root not found above code source: $codeSource")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val moduleRoot: String by lazy {
        moduleRootOverride?.toString() ?: moduleRootImpl()
    }

    private val scanRoots: List<Path> by lazy { scanRootsImpl() }


    //-----------------------------------------------------------------------------------------------------------------
    override fun scanRoots(): List<Path> {
        return scanRoots
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun locateExisting(location: DocumentPath): Path? {
        for (root in scanRoots) {
            val candidate = root.resolve(location.asRelativeFile())

            if (Files.exists(candidate)) {
                return candidate
            }
        }

        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun resolveNew(location: DocumentPath): Path? {
        return Paths.get("${mainResources()}/${location.asRelativeFile()}")
                .normalize()
                .toAbsolutePath()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainResources(): String {
        return "$moduleRoot$mainResources"
    }


    private fun scanRootsImpl(): List<Path> {
        val buffer = mutableListOf<String>()

        buffer.add(mainResources())

        if (includeTest) {
            buffer.add("$moduleRoot$testResources")
        }

        return buffer.map { Paths.get(it).normalize().toAbsolutePath() }
    }


    private fun moduleRootImpl(): String {
        if (Files.exists(Paths.get("src"))) {
            return "."
        }

        return Files.list(Paths.get(".")).use { files ->
            val list = files.collect(Collectors.toList())

            val jvmModule = list.firstOrNull { it.fileName.toString().endsWith("-jvm")}

            // TODO: expose static/
            jvmModule?.toString()
                ?: "."
        }
    }
}