package tech.kzen.lib.server.notation

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
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
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.platform.toInputStream
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import java.io.ByteArrayInputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant


class FileNotationMedia(
        private val notationLocator: FileNotationLocator
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private val notationScanMirror = mutableMapOf<DocumentPath, DocumentScan>()
    private var notationScanCache: NotationScan? = null

    private val resourceScanMirror = mutableMapOf<DocumentPath, ResourceListing>()

    private val documentInfoCache: Cache<DocumentPath, FileInfo> = CacheBuilder
            .newBuilder()
            .maximumSize(1024)
            .build()

    private val documentCache: Cache<Digest, String> = CacheBuilder
            .newBuilder()
            .maximumWeight(1024L * 1024)
            .weigher { _: Digest, value: String -> value.length }
            .build()

    private val resourceInfoCache: Cache<ResourceLocation, FileInfo> = CacheBuilder
            .newBuilder()
            .maximumSize(1024)
            .build()

    private data class FileInfo(
            val path: Path,
            var modified: Instant,
            var digest: Digest)


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override suspend fun scan(): NotationScan {
        if (notationScanCache != null) {
            return notationScanCache!!
        }

        if (notationScanMirror.isNotEmpty()) {
            notationScanCache = NotationScan(DocumentPathMap(notationScanMirror.toPersistentMap()))
            return notationScanCache!!
        }

        val roots = notationLocator.scanRoots()

        for (root in roots) {
            scanRootIntoMirror(root)
        }

        notationScanCache = NotationScan(DocumentPathMap(notationScanMirror.toPersistentMap()))
        return notationScanCache!!
    }


    private fun scanRootIntoMirror(
            root: Path
    ) {
        val locationTimes = scanDocumentModifiedTimes(root)

        for ((documentPath, modified) in locationTimes) {
            val cachedInfo = documentInfoCache.getIfPresent(documentPath)

            val resources =
                    if (documentPath.directory) {
                        resourceScan(documentPath, root)
                    }
                    else {
                        null
                    }

            val resolvedDocumentPath = root.resolve(
                    documentPath.asRelativeFile())

            val documentScan =
                if (cachedInfo == null) {
                    val digest = digestFile(resolvedDocumentPath)
                    documentInfoCache.put(documentPath, FileInfo(
                            resolvedDocumentPath, modified, digest))

                    DocumentScan(
                            digest,
                            resources)
                }
                else {
                    if (cachedInfo.modified != modified) {
                        cachedInfo.digest = digestFile(resolvedDocumentPath)
                        cachedInfo.modified = modified
                    }

                    DocumentScan(
                            cachedInfo.digest,
                            resources)
                }

            notationScanMirror[documentPath] = documentScan
        }
    }


    private fun scanDocumentModifiedTimes(
            root: Path
    ): Map<DocumentPath, Instant> {
        if (! Files.exists(root)) {
            return mapOf()
        }

        val locationTimes =
                mutableMapOf<DocumentPath, Instant>()

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val possibleDirectoryDocument = dir!!.resolve(NotationConventions.directoryDocumentName)

                if (Files.exists(possibleDirectoryDocument)) {
                    val relative = root.relativize(possibleDirectoryDocument).toString()
                    val normalized = relative.replace('\\', '/')
                    check(DocumentPath.matches(normalized))

                    val documentPath = DocumentPath.parse(normalized)
                    locationTimes[documentPath] = attrs!!.lastModifiedTime().toInstant()
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

                    val documentPath = DocumentPath.parse(normalized)
                    locationTimes[documentPath] = attrs!!.lastModifiedTime().toInstant()
                }

                return FileVisitResult.CONTINUE
            }
        })

        return locationTimes
    }


    private fun resourceScan(
            documentPath: DocumentPath,
            root: Path
    ): ResourceListing {
        val mirrored = resourceScanMirror[documentPath]
        if (mirrored != null) {
            return mirrored
        }

        val resolvedDocumentDir = root
                .resolve(documentPath.nesting.asString())
                .resolve(documentPath.name.value)

        val builder = mutableMapOf<ResourcePath, Digest>()

        Files.walkFileTree(resolvedDocumentDir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val fileName = file!!.fileName.toString()
                if (fileName == NotationConventions.directoryDocumentName) {
                    return FileVisitResult.CONTINUE
                }

                val relative = resolvedDocumentDir.relativize(file).toString()
                val normalized = relative.replace('\\', '/')

                val resourcePath = ResourcePath.parse(normalized)

                val resourceLocation = ResourceLocation(documentPath, resourcePath)

                val modified = Files.getLastModifiedTime(file).toInstant()

                val cachedResourceInfo = resourceInfoCache.get(resourceLocation) {
                    FileInfo(file, Instant.MIN, Digest.zero)
                }

                val digest =
                        if (cachedResourceInfo.modified == modified) {
                            cachedResourceInfo.digest
                        }
                        else {
                            val bytes = Files.readAllBytes(file)
                            val newDigest = Digest.ofBytes(bytes)
                            cachedResourceInfo.digest = newDigest
                            cachedResourceInfo.modified = modified
                            newDigest
                        }

                builder[resourcePath] = digest

                return FileVisitResult.CONTINUE
            }
        })

        val resourceListing = ResourceListing(builder.toPersistentMap())

        resourceScanMirror[documentPath] = resourceListing

        return resourceListing
    }


    private fun digestFile(
            path: Path
    ): Digest {
        val bytes = Files.readAllBytes(path)
        return Digest.ofBytes(bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        val cachedDocumentInfo: FileInfo?
        var resolvedDocumentPath: Path? = null
        var modified: Instant? = null

        if (expectedDigest != null) {
            val cached = documentCache.getIfPresent(expectedDigest)
            if (cached != null) {
                return cached
            }
            cachedDocumentInfo = documentInfoCache.getIfPresent(documentPath)
        }
        else {
            cachedDocumentInfo = documentInfoCache.getIfPresent(documentPath)
            if (cachedDocumentInfo != null) {
                resolvedDocumentPath = cachedDocumentInfo.path
                modified = Files.getLastModifiedTime(resolvedDocumentPath).toInstant()

                if (cachedDocumentInfo.modified == modified) {
                    val cached = documentCache.getIfPresent(cachedDocumentInfo.digest)
                    if (cached != null) {
                        return cached
                    }
                }
            }
        }

        if (resolvedDocumentPath == null) {
            resolvedDocumentPath = cachedDocumentInfo?.path
                    ?: notationLocator.locateExisting(documentPath)
                    ?: throw IllegalArgumentException("Not found: $documentPath")
        }

        val bytes = Files.readAllBytes(resolvedDocumentPath)

        val digest = Digest.ofBytes(bytes)
        if (expectedDigest != null) {
            check(digest == expectedDigest) {
                "Unexpected digest: $documentPath - $expectedDigest - $digest"
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        val contents = String(bytes, Charsets.UTF_8)

        if (modified == null) {
            modified = Files.getLastModifiedTime(resolvedDocumentPath).toInstant()
        }

        documentCache.put(digest, contents)
        documentInfoCache.put(documentPath, FileInfo(
                resolvedDocumentPath, modified!!, digest))

        return contents
    }


    @Synchronized
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

        val bytes = contents.toByteArray()
        Files.write(path, bytes)

        val modified = Files.getLastModifiedTime(path).toInstant()
        val digest = Digest.ofBytes(bytes)

        invalidateDocumentContents(documentPath, path, modified, contents, digest)
    }


    @Synchronized
    override suspend fun deleteDocument(documentPath: DocumentPath) {
        val path = notationLocator.locateExisting(documentPath)
                ?: throw IllegalArgumentException("Not found: $documentPath")

        if (documentPath.directory) {
            MoreFiles.deleteRecursively(path.parent, RecursiveDeleteOption.ALLOW_INSECURE)
        }
        else {
            Files.delete(path)
        }

        invalidateDocument(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        val resolvedDocumentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resolvedResourcePath = resolvedDocumentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())

        val bytes = Files.readAllBytes(resolvedResourcePath)

//        val resourceCache

        return ImmutableByteArray.wrap(bytes)
    }


    @Synchronized
    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        val documentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val resourcePath = documentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())

        Files.createDirectories(resourcePath.parent)

        Files.copy(contents.toInputStream(), resourcePath, StandardCopyOption.REPLACE_EXISTING)

        val digest = contents.digest()
        val modified = Files.getLastModifiedTime(resourcePath).toInstant()

        invalidateUpsertResource(
                resourcePath, resourceLocation, digest, modified)
    }


    @Synchronized
    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        val sourceDocumentPath = notationLocator.locateExisting(resourceLocation.documentPath)
                ?: throw IllegalArgumentException("Not found: ${resourceLocation.documentPath}")

        val sourceResourcePath = sourceDocumentPath.resolveSibling(
                resourceLocation.resourcePath.asRelativeFile())

        val destinationDocumentPath = notationLocator.locateExisting(destination.documentPath)
                ?: throw IllegalArgumentException("Not found: ${destination.documentPath}")

        val destinationResourcePath = destinationDocumentPath.resolveSibling(
                destination.resourcePath.asRelativeFile())

        Files.createDirectories(destinationResourcePath.parent)

        val contents = Files.readAllBytes(sourceResourcePath)
        Files.copy(ByteArrayInputStream(contents), destinationResourcePath)
        // Files.copy(sourceResourcePath, destinationResourcePath)

        val modified = Files.getLastModifiedTime(destinationResourcePath).toInstant()
        val digest = Digest.ofBytes(contents)

        invalidateUpsertResource(
                destinationResourcePath, resourceLocation, digest, modified)
    }


    @Synchronized
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

        invalidateRemovedResource(resourceLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun invalidate() {
        notationScanMirror.clear()
        resourceScanMirror.clear()
        notationScanCache = null
    }


    private fun invalidateDocument(
            documentPath: DocumentPath
    ) {
        documentInfoCache.invalidate(documentPath)

        val previousMirror = notationScanMirror.remove(documentPath)
        if (previousMirror != null) {
            documentCache.invalidate(previousMirror.documentDigest)
            resourceScanMirror.remove(documentPath)
        }
        else {
            notationScanMirror.clear()
            resourceScanMirror.clear()
        }

        notationScanCache = null
    }


    private fun invalidateDocumentContents(
            documentPath: DocumentPath,
            resolvedDocumentPath: Path,
            modified: Instant,
            contents: String,
            documentDigest: Digest
    ) {
        documentCache.put(documentDigest, contents)
        documentInfoCache.put(documentPath, FileInfo(
                resolvedDocumentPath, modified, documentDigest))

        val previousMirror = notationScanMirror[documentPath]
        if (previousMirror != null) {
            notationScanMirror[documentPath] = previousMirror.copy(documentDigest = documentDigest)
        }
        else {
            notationScanMirror.clear()
            resourceScanMirror.clear()
        }

        notationScanCache = null
    }


    private fun invalidateRemovedResource(
            resourceLocation: ResourceLocation
    ) {
        invalidateResource(resourceLocation) {
            it.withoutResource(resourceLocation.resourcePath)
        }

        resourceInfoCache.invalidate(resourceLocation)
    }


    private fun invalidateUpsertResource(
            resolvedResourcePath: Path,
            resourceLocation: ResourceLocation,
            resourceDigest: Digest,
            resourceModified: Instant
    ) {
        invalidateResource(resourceLocation) {
            it.withResource(resourceLocation.resourcePath, resourceDigest)
        }

        val cachedResourceInfo = resourceInfoCache.getIfPresent(resourceLocation)
                ?: FileInfo(resolvedResourcePath, Instant.MIN, Digest.zero)

        if (cachedResourceInfo.modified != resourceModified) {
            cachedResourceInfo.modified = resourceModified
            cachedResourceInfo.digest = resourceDigest
        }
    }


    private fun invalidateResource(
            resourceLocation: ResourceLocation,
            transform: (ResourceListing) -> ResourceListing
    ) {
        val previousNotationMirror = notationScanMirror[resourceLocation.documentPath]
        val previousResourceMirror = resourceScanMirror[resourceLocation.documentPath]

        if (previousNotationMirror != null && previousResourceMirror != null) {
            val newResourceMirror = transform.invoke(previousResourceMirror)

            resourceScanMirror[resourceLocation.documentPath] = newResourceMirror

            notationScanMirror[resourceLocation.documentPath] = previousNotationMirror
                    .copy(resources = newResourceMirror)
        }
        else {
            notationScanMirror.clear()
            resourceScanMirror.clear()
        }

        notationScanCache = null
    }
}