package tech.kzen.lib.server.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant


class FileNotationMedia(
        private val notationLocator: FileNotationLocator
) : NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val digestCache: MutableMap<DocumentPath, TimedDigest> = mutableMapOf()

    private data class TimedDigest(
            var modified: Instant,
            var digest: Digest)


    private suspend fun digest(
            path: DocumentPath
    ): Digest {
        val bytes = read(path)
        return Digest.ofXoShiRo256StarStar(bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): DocumentPathMap<Digest> {
        val locationTimes = mutableMapOf<DocumentPath, Instant>()

        val roots = notationLocator.scanRoots()
        for (root in roots) {
            directoryScan(root, locationTimes)
        }

        val digested = mutableMapOf<DocumentPath, Digest>()

        for (e in locationTimes) {
            val timedDigest = digestCache[e.key]

            if (timedDigest == null) {
                val digest = digest(e.key)
                digestCache[e.key] = TimedDigest(e.value, digest)
                digested[e.key] = digest
            }
            else {
                if (timedDigest.modified != e.value) {
                    timedDigest.digest = digest(e.key)
                    timedDigest.modified = e.value
                }

                digested[e.key] = timedDigest.digest
            }
        }

        return DocumentPathMap(digested)
    }


    private fun directoryScan(
            root: Path,
            locationTimes: MutableMap<DocumentPath, Instant>
    ) {
        if (! Files.exists(root)) {
            return
        }

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (file!!.fileName.toString().endsWith(".yaml")) {
                    val relative = root.relativize(file).toString()
                    val normalized = relative.replace('\\', '/')

                    val path = DocumentPath.parse(normalized)
                    val modified = attrs!!.lastModifiedTime().toInstant()

                    locationTimes[path] = modified
                }

                return FileVisitResult.CONTINUE
            }
        })
    }



    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: DocumentPath): ByteArray {
        val path = notationLocator.locateExisting(location)
                ?: throw IllegalArgumentException("Not found: $location")

//        println("GradleNotationMedia | read - moduleRoot: ${path.toAbsolutePath().normalize()}")

        return Files.readAllBytes(path)
    }


    override suspend fun write(location: DocumentPath, bytes: ByteArray) {
        val existingPath = notationLocator.locateExisting(location)

        val path = if (existingPath != null) {
            existingPath
        }
        else {
            val resolvedPath = notationLocator.resolveNew(location)
                    ?: throw IllegalArgumentException("Unable to resolve: $location")

            val parent = resolvedPath.parent
            println("GradleNotationMedia | write - creating parent directory: $parent")
            Files.createDirectories(parent)

            resolvedPath
        }

        println("GradleNotationMedia | write - moduleRoot: $path | ${bytes.size}")

        Files.write(path, bytes)
    }


    override suspend fun delete(location: DocumentPath) {
        val path = notationLocator.locateExisting(location)
                ?: throw IllegalArgumentException("Not found: $location")

        Files.delete(path)
    }
}