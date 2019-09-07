package tech.kzen.lib.server.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.resource.ResourceInfo
import tech.kzen.lib.common.model.resource.ResourceListing
import tech.kzen.lib.common.model.resource.ResourcePath
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.structure.notation.io.model.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap
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


    private data class RootedDocumentPath(
            var root: Path,
            var documentPath: DocumentPath)


    private suspend fun digest(
            path: DocumentPath
    ): Digest {
        val bytes = read(path)
        return Digest.ofBytes(bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        val locationTimes =
                mutableMapOf<RootedDocumentPath, BasicFileAttributes>()

        val roots = notationLocator.scanRoots()
        for (root in roots) {
            directoryScan(root, locationTimes)
        }

        val digested = mutableMapOf<DocumentPath, DocumentScan>()

        for (e in locationTimes) {
            val documentPath = e.key.documentPath
            val timedDigest = digestCache[documentPath]
            val modified = e.value.lastModifiedTime().toInstant()

            val resources =
                    if (documentPath.directory) {
                        scanResources(e.key.root, documentPath)
                    }
                    else {
                        null
                    }

            if (timedDigest == null) {
                val digest = digest(documentPath)
                digestCache[documentPath] = TimedDigest(modified, digest)

                digested[documentPath] = DocumentScan(
                        digest,
                        resources)
            }
            else {
                if (timedDigest.modified != modified) {
                    timedDigest.digest = digest(documentPath)
                    timedDigest.modified = modified
                }

                digested[documentPath] = DocumentScan(
                        timedDigest.digest,
                        resources)
            }
        }

        return NotationScan(DocumentPathMap(digested.toPersistentMap()))
    }


    private fun directoryScan(
            root: Path,
            locationTimes: MutableMap<RootedDocumentPath, BasicFileAttributes>
    ) {
        if (! Files.exists(root)) {
            return
        }

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val possibleDirectoryDocument = dir!!.resolve(NotationConventions.directoryDocumentName)

                if (Files.exists(possibleDirectoryDocument)) {
                    val relative = root.relativize(possibleDirectoryDocument).toString()
                    val normalized = relative.replace('\\', '/')
                    check(DocumentPath.matches(normalized))

                    val rootedDocumentPath = RootedDocumentPath(root, DocumentPath.parse(normalized))
                    locationTimes[rootedDocumentPath] = attrs!!
                    return FileVisitResult.SKIP_SUBTREE
                }

                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val fileName = file!!.fileName.toString()

                if (fileName.endsWith(NotationConventions.fileDocumentSuffix)) {
                    val relative = root.relativize(file).toString()
                    val normalized = relative.replace('\\', '/')
                    check(DocumentPath.matches(normalized))

                    val rootedDocumentPath = RootedDocumentPath(root, DocumentPath.parse(normalized))
                    locationTimes[rootedDocumentPath] = attrs!!
                }

                return FileVisitResult.CONTINUE
            }
        })
    }


    private fun scanResources(
            root: Path,
            documentPath: DocumentPath
    ): ResourceListing {
        check(documentPath.directory)

        val resolvedDocumentDir = root
                .resolve(documentPath.nesting.asString())
                .resolve(documentPath.name.value)

        val listing = mutableMapOf<ResourcePath, ResourceInfo>()

        Files.walkFileTree(resolvedDocumentDir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val fileName = file!!.fileName.toString()
                if (fileName == NotationConventions.directoryDocumentName) {
                    return FileVisitResult.CONTINUE
                }

                val relative = resolvedDocumentDir.relativize(file).toString()
                val normalized = relative.replace('\\', '/')

                val resourcePath = ResourcePath.parse(normalized)
                val digest = Digest.ofBytes(Files.readAllBytes(file))

                listing[resourcePath] = ResourceInfo(
                        attrs!!.size().toInt(),
                        digest)

                return FileVisitResult.CONTINUE
            }
        })

        return ResourceListing(listing)
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