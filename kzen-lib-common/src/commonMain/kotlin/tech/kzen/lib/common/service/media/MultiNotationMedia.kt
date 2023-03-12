package tech.kzen.lib.common.service.media
//
//import tech.kzen.lib.common.model.document.DocumentPath
//import tech.kzen.lib.common.model.document.DocumentPathMap
//import tech.kzen.lib.common.model.locate.ResourceLocation
//import tech.kzen.lib.common.model.structure.scan.DocumentScan
//import tech.kzen.lib.common.model.structure.scan.NotationScan
//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.common.util.ImmutableByteArray
//import tech.kzen.lib.platform.collect.toPersistentMap
//
///**
// * scans all media (duplicates allowed), reads/writes first available medium, deletes from all media
// */
//class MultiNotationMedia(
//    private val media: List<NotationMedia>
//):
//    NotationMedia
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private var scanCache: NotationScan? = null
//    private var scanCacheDigest = Digest.zero
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun scan(): NotationScan {
//        val digestBuilder = Digest.Builder()
//
//        val scans = mutableListOf<NotationScan>()
//        for (delegate in media) {
//            val scan = delegate.scan()
//            scans.add(scan)
//            digestBuilder.addDigest(scan.digest())
//        }
//
//        val cacheDigest = digestBuilder.digest()
//        if (scanCacheDigest == cacheDigest) {
//            return scanCache!!
//        }
//
//        val all = mutableMapOf<DocumentPath, DocumentScan>()
//        for (scan in scans) {
//            for (e in scan.documents.values) {
//                if (e.key in all) {
//                    continue
//                }
//
//                all[e.key] = e.value
//            }
//        }
//
//        val consolidated = NotationScan(DocumentPathMap(all.toPersistentMap()))
//
//        scanCache = consolidated
//        scanCacheDigest = cacheDigest
//
//        return consolidated
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
//        for (source in media) {
//            if (! source.containsDocument(documentPath)) {
//                continue
//            }
//
//            return source.readDocument(documentPath, expectedDigest)
//        }
//
//        throw IllegalArgumentException("Not found: $documentPath")
//    }
//
//
//    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
//        for (medium in media) {
//            try {
//                medium.writeDocument(documentPath, contents)
//                return
//            }
//            catch (ignored: Exception) {}
//        }
//
//        throw IllegalArgumentException("Unable to write: $documentPath")
//    }
//
//
//    override suspend fun deleteDocument(documentPath: DocumentPath) {
//        var found = false
//        for (medium in media) {
//            if (! medium.containsDocument(documentPath)) {
//                continue
//            }
//
//            medium.deleteDocument(documentPath)
//            found = true
//        }
//
//        if (! found) {
//            throw IllegalArgumentException("Cannot delete, document not found: $documentPath")
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
//        for (source in media) {
//            if (! source.containsResource(resourceLocation)) {
//                continue
//            }
//
//            return source.readResource(resourceLocation)
//        }
//
//        throw IllegalArgumentException("Cannot read, resource not found: $resourceLocation")
//    }
//
//
//    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
//        for (medium in media) {
//            try {
//                medium.writeResource(resourceLocation, contents)
//                return
//            }
//            catch (ignored: Exception) {}
//        }
//
//        throw IllegalArgumentException("Unable to write: $resourceLocation")
//    }
//
//
//    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
//        for (medium in media) {
//            if (! medium.containsResource(resourceLocation)) {
//                continue
//            }
//
//            medium.copyResource(resourceLocation, destination)
//        }
//
//        throw IllegalArgumentException("Cannot copy resource, not found: $resourceLocation - $destination")
//    }
//
//
//    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
//        var found = false
//        for (medium in media) {
//            if (! medium.containsResource(resourceLocation)) {
//                continue
//            }
//
//            medium.deleteResource(resourceLocation)
//            found = true
//        }
//
//        if (! found) {
//            throw IllegalArgumentException("Cannot delete, resource not found: $resourceLocation")
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun invalidate() {
//        for (medium in media) {
//            medium.invalidate()
//        }
//    }
//}