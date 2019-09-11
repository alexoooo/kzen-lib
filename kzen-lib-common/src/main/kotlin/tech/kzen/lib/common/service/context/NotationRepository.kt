package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationAggregate
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.util.Cache
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


// TODO: threadsafe?
class NotationRepository(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser,
        private val metadataReader: NotationMetadataReader,
        private val graphDefiner: GraphDefiner
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache = mutableMapOf<DocumentPath, DocumentScan>()

    // TODO: use notation from inside ProjectAggregate?
    private var projectNotationCache: GraphNotation? = null
    private var projectAggregateCache: NotationAggregate? = null
    private var fileCache = Cache<String>(10)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun notation(): GraphNotation {
        if (projectNotationCache == null) {
            projectNotationCache = read()
        }
        return projectNotationCache!!
    }


    private suspend fun read(): GraphNotation {
        val documentBodies = mutableMapOf<DocumentPath, String>()
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
                    ?: notationMedia.readDocument(projectPath)

            documentBodies[projectPath] = body

            val documentNotation = notationParser.parseDocumentObjects(body)
            documents[projectPath] = DocumentNotation(documentNotation, e.value.resources)
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
                        val graphDefinition = graphDefiner.define(GraphStructure(notation, graphMetadata))
                        aggregate.apply(command, graphDefinition)
                    }
                }

        val newDocuments = aggregate.state.documents

        var writtenAny = false
        for (updatedDocument in newDocuments.values) {
            val oldDocument = oldDocuments.values[updatedDocument.key]

            if (oldDocument != null &&
                    updatedDocument.value.objects.equalsInOrder(oldDocument.objects) &&
                    updatedDocument.value.resources == oldDocument.resources) {
                continue
            }

            val written = writeIfRequired(
                    updatedDocument.key, updatedDocument.value, command)

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
            documentNotation: DocumentNotation,
            command: NotationCommand
    ): Boolean {
        val previousDocumentScan = scanCache[documentPath]
        val previousDigest = previousDocumentScan?.documentDigest

        var previousMissing = false
        val previousBody: String =
                if (previousDigest == null) {
                    previousMissing = true
                    ""
                }
                else {
                    val cached = fileCache.get(previousDigest)
                    if (cached == null) {
                        previousMissing = true
                        ""
                    }
                    else {
                        cached
                    }
                }

        val updatedBody = notationParser.unparseDocument(documentNotation, previousBody)
//        println("!!! updatedBody: ${IoUtils.utf8ToString(updatedBody)}")

        var modified = false

        if (updatedBody != previousBody || previousMissing) {
            notationMedia.writeDocument(documentPath, updatedBody)
            modified = true
        }

        when (command) {
            is CopyDocumentCommand -> {
                val originalDocumentPath = command.sourceDocumentPath
                val originalResources = scanCache[originalDocumentPath]?.resources!!

                for (resourcePath in originalResources.values.keys) {
                    val contents = notationMedia.readResource(
                            ResourceLocation(originalDocumentPath, resourcePath))

                    notationMedia.writeResource(
                            ResourceLocation(documentPath, resourcePath),
                            contents)
                }
            }

            is ResourceNotationCommand -> {
                when (command) {
                    is AddResourceCommand -> {
                        notationMedia.writeResource(
                                command.resourceLocation,
                                command.resourceContent.value)
                    }

                    is RemoveResourceCommand -> {
                        notationMedia.deleteResource(command.resourceLocation)
                    }
                }

                modified = true
            }

        }

        scanCache[documentPath] = DocumentScan(
                Digest.ofUtf8(updatedBody),
                documentNotation.resources)

        return modified
    }


    private suspend fun delete(
            documentPath: DocumentPath
    ) {
        notationMedia.deleteDocument(documentPath)
        scanCache.remove(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun digest(): Digest {
        if (scanCache.isEmpty()) {
            read()
        }

        val digester = Digest.Builder()
        digester.addDigestibleUnorderedMap(scanCache)
        return digester.digest()
    }
}