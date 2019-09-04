package tech.kzen.lib.common.structure.notation.repo

import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.util.Cache
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


// TODO: threadsafe?
class NotationRepository(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser,
        private val metadataReader: NotationMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache = mutableMapOf<DocumentPath, DocumentScan>()

    // TODO: use notation from inside ProjectAggregate?
    private var projectNotationCache: GraphNotation? = null
    private var projectAggregateCache: NotationAggregate? = null
    private var fileCache = Cache<ByteArray>(10)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun notation(): GraphNotation {
        if (projectNotationCache == null) {
            projectNotationCache = read()
        }
        return projectNotationCache!!
    }


    private suspend fun read(): GraphNotation {
        val packageBytes = mutableMapOf<DocumentPath, ByteArray>()
        val documents = mutableMapOf<DocumentPath, DocumentNotation>()

        scanCache.clear()
        scanCache.putAll(notationMedia.scan().documents.values)

        if (scanCache.size * 2 > fileCache.size) {
            fileCache.size = scanCache.size * 2
        }

        for (e in scanCache) {
            val projectPath = e.key

            val bodyCache = fileCache.get(e.value.documentDigest)

            val body = bodyCache
                    ?: notationMedia.read(projectPath)

            packageBytes[projectPath] = body

            val documentNotation = notationParser.parseDocumentObjects(body)
            documents[projectPath] = DocumentNotation(documentNotation, null)
        }

        return GraphNotation(DocumentPathMap(documents.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun aggregate(): NotationAggregate {
        if (projectAggregateCache == null) {
            projectAggregateCache = NotationAggregate(notation())
        }
        return projectAggregateCache!!
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun clearCache() {
        scanCache.clear()
        projectNotationCache = null
        projectAggregateCache = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: NotationCommand): NotationEvent {
        val notation = notation()
        val aggregate = aggregate()

        val oldDocuments = notation.documents

        val event =
                when (command) {
                    is StructuralNotationCommand ->
                        aggregate.apply(command)

                    is SemanticNotationCommand -> {
                        val graphMetadata = metadataReader.read(notation)
                        val graphDefinition = GraphDefiner.define(GraphStructure(notation, graphMetadata))
                        aggregate.apply(command, graphDefinition)
                    }
                }


        val newDocuments = aggregate.state.documents

        var writtenAny = false
        for (updatedDocument in newDocuments.values) {
            if (oldDocuments.values.containsKey(updatedDocument.key) &&
                    updatedDocument.value.objects.equalsInOrder(oldDocuments.values[updatedDocument.key]!!.objects)) {
                continue
            }

            val written = writeIfRequired(updatedDocument.key, updatedDocument.value)
            writtenAny = writtenAny || written
        }
        for (removed in oldDocuments.values.keys.minus(newDocuments.values.keys)) {
            delete(removed)
            writtenAny = true
        }

        if (writtenAny) {
            // TODO: avoid needless clearing
            clearCache()
        }

        return event
    }


    private suspend fun writeIfRequired(
            documentPath: DocumentPath,
            documentNotation: DocumentNotation
    ): Boolean {
        val cachedDigest = scanCache[documentPath]?.documentDigest

        var previousMissing = false
        val previousBody: ByteArray =
                if (cachedDigest == null) {
                    previousMissing = true
                    ByteArray(0)
                }
                else {
                    val cached = fileCache.get(cachedDigest)
                    if (cached == null) {
                        previousMissing = true
                        ByteArray(0)
                    }
                    else {
                        cached
                    }
                }

        val updatedBody = notationParser.deparseDocument(documentNotation, previousBody)
//        println("!!! updatedBody: ${IoUtils.utf8ToString(updatedBody)}")

        if (updatedBody.contentEquals(previousBody) && ! previousMissing) {
            return false
        }

        notationMedia.write(documentPath, updatedBody)

        scanCache[documentPath] = DocumentScan(
                Digest.ofXoShiRo256StarStar(updatedBody),
                null)

        return true
    }


    private suspend fun delete(
            documentPath: DocumentPath
    ) {
        notationMedia.delete(documentPath)
        scanCache.remove(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun digest(): Digest {
        if (scanCache.isEmpty()) {
            read()
        }

        val digester = Digest.Streaming()
        digester.addDigestibleUnorderedMap(scanCache)
        return digester.digest()
    }
}