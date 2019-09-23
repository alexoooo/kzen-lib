package tech.kzen.lib.server.notation

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
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
        val bytes = readDocument(path)
        return Digest.ofUtf8(bytes)
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

        val listing = mutableMapOf<ResourcePath, Digest>()

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

                listing[resourcePath] = digest

                return FileVisitResult.CONTINUE
            }
        })

        return ResourceListing(listing.toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): String {
        val path = notationLocator.locateExisting(documentPath)
                ?: throw IllegalArgumentException("Not found: $documentPath")

//        println("GradleNotationMedia | read - moduleRoot: ${path.toAbsolutePath().normalize()}")

//        return Files.readAllBytes(path)
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        val existingPath = notationLocator.locateExisting(documentPath)

        val path = if (existingPath != null) {
            existingPath
        }
        else {
            val resolvedPath = notationLocator.resolveNew(documentPath)
                    ?: throw IllegalArgumentException("Unable to resolve: $documentPath")

            val parent = resolvedPath.parent
//            println("FileNotationMedia | write - creating parent directory: $parent")
            Files.createDirectories(parent)

            resolvedPath
        }

//        println("FileNotationMedia | write - moduleRoot: $path | ${contents.size}")
//        Files.write(path, contents)
        Files.write(path, contents.toByteArray())
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        val path = notationLocator.locateExisting(documentPath)
                ?: throw IllegalArgumentException("Not found: $documentPath")

        if (documentPath.directory) {
            MoreFiles.deleteRecursively(path.parent, RecursiveDeleteOption.ALLOW_INSECURE)
        }
        else {
            Files.delete(path)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        val documentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resourcePath = documentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())

        return Files.readAllBytes(resourcePath)
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        val documentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resourcePath = documentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())

        Files.createDirectories(resourcePath.parent)

        Files.write(resourcePath, contents)
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        val documentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resourcePath = documentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())
        check(Files.exists(resourcePath)) {
            "Resource not found: $resourceLocation"
        }

        Files.delete(resourcePath)

        var dirCursor = resourcePath.parent
        while (MoreFiles.listFiles(dirCursor).isEmpty()) {
            Files.delete(dirCursor)
            dirCursor = dirCursor.parent
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        // NB: currently NOOP, but would apply to scan cache (if it were implemented)
    }
}