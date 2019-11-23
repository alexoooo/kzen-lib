package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.toPersistentMap


class MultiNotationMedia(
        private val media: List<NotationMedia>
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache: NotationScan? = null
    private var scanCacheDigest = Digest.zero


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        val digestBuilder = Digest.Builder()

        val scans = mutableListOf<NotationScan>()
        for (delegate in media) {
            val scan = delegate.scan()
            scans.add(scan)
            digestBuilder.addDigest(scan.digest())
        }

        val cacheDigest = digestBuilder.digest()
        if (scanCacheDigest == cacheDigest) {
            return scanCache!!
        }

        val duplicates = mutableSetOf<DocumentPath>()

        val all = mutableMapOf<DocumentPath, DocumentScan>()
        for (scan in scans) {
            for (e in scan.documents.values) {
                if (all.containsKey(e.key)) {
                    duplicates.add(e.key)
                }
                else {
                    all[e.key] = e.value
                }
            }
        }

        check(duplicates.isEmpty()) { "Duplicates detected: $duplicates" }

        val consolidated = NotationScan(DocumentPathMap(all.toPersistentMap()))

        scanCache = consolidated
        scanCacheDigest = cacheDigest

        return consolidated
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        for (source in media) {
            try {
                return source.readDocument(documentPath, expectedDigest)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Not found in $source - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $documentPath")
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        for (medium in media) {
            try {
                return medium.writeDocument(documentPath, contents)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $documentPath")
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        for (medium in media) {
            try {
                return medium.copyResource(resourceLocation, destination)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to copy: $resourceLocation - $destination")
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        for (medium in media) {
            try {
                return medium.deleteDocument(documentPath)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Can't delete in $medium - " +
//                        "${ignored::class.simpleName} - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $documentPath")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        for (source in media) {
            try {
                return source.readResource(resourceLocation)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Not found in $source - " +
//                        "${ignored::class.simpleName} - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $resourceLocation")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        for (medium in media) {
            try {
                return medium.writeResource(resourceLocation, contents)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $resourceLocation")
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        for (medium in media) {
            try {
                return medium.deleteResource(resourceLocation)
            }
            catch (ignored: Exception) {
//                println("MultiNotationMedia - Can't delete in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $resourceLocation")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        for (medium in media) {
            medium.invalidate()
        }
    }
}