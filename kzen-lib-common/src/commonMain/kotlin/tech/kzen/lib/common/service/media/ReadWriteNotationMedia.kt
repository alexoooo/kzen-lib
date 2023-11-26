package tech.kzen.lib.common.service.media

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.collect.PersistentMap


/**
 * scans all media (duplicates not allowed), reads first available (readable/writable), writes/copies/deletes from writable
 */
class ReadWriteNotationMedia(
    private val writable: NotationMedia,
    private val readOnly: NotationMedia
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache: NotationScan? = null
    private var scanCacheDigest = Digest.zero


    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(! writable.isReadOnly())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun isReadOnly(): Boolean {
        return false
    }


    override suspend fun scan(): NotationScan {
        val digestBuilder = Digest.Builder()

        val writableScan = writable.scan()
        digestBuilder.addDigest(writableScan.digest())

        val readOnlyScan = readOnly.scan()
        digestBuilder.addDigest(readOnlyScan.digest())

        val cacheDigest = digestBuilder.digest()
        if (scanCacheDigest == cacheDigest) {
            return scanCache!!
        }

        val writableDocuments = writableScan.documents.values
        val readOnlyDocuments = readOnlyScan.documents.values

        val merged: PersistentMap<DocumentPath, DocumentScan> = writableDocuments.putAll(readOnlyDocuments)
        val totalIndividualSize = writableDocuments.size + readOnlyDocuments.size

        check(totalIndividualSize == merged.size) {
            val duplicates = writableDocuments.keys.intersect(readOnlyDocuments.keys)
            "Duplicate detected: $duplicates"
        }

        val scan = NotationScan(DocumentPathMap(merged))

        scanCache = scan
        scanCacheDigest = cacheDigest

        return scan
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath, expectedDigest: Digest?): String {
        if (readOnly.containsDocument(documentPath)) {
            return readOnly.readDocument(documentPath)
        }

        check(writable.containsDocument(documentPath)) {
            "Cannot read document, not found: $documentPath"
        }

        return writable.readDocument(documentPath)
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        check(! readOnly.containsDocument(documentPath)) {
            "Writing document overlap with read-only: $documentPath"
        }

        writable.writeDocument(documentPath, contents)
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        writable.deleteDocument(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ImmutableByteArray {
        if (readOnly.containsResource(resourceLocation)) {
            return readOnly.readResource(resourceLocation)
        }

        return writable.readResource(resourceLocation)
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ImmutableByteArray) {
        check(! readOnly.containsResource(resourceLocation)) {
            "Writing resource overlap with read-only: $resourceLocation"
        }

        writable.writeResource(resourceLocation, contents)
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        check(! readOnly.containsResource(destination)) {
            "Copying resource overlap with read-only: $destination"
        }

        writable.copyResource(resourceLocation, destination)
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        writable.deleteResource(resourceLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        readOnly.invalidate()
        writable.invalidate()
    }
}