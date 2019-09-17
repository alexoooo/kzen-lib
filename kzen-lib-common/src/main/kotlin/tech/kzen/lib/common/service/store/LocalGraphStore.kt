package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class LocalGraphStore(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser,
        private val notationMetadataReader: NotationMetadataReader,
        private val graphDefiner: GraphDefiner,
        private val notationReducer: NotationReducer
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var graphNotationCacheDigest: Digest? = null
    private var graphNotationCache: GraphNotation? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun graphNotation(): GraphNotation {
        val digest = digest()

        if (graphNotationCacheDigest != digest) {
            graphNotationCacheDigest = digest
            graphNotationCache = graphNotationImpl()
        }

        return graphNotationCache!!
    }


    private suspend fun graphNotationImpl(): GraphNotation {
        val documentBodies = mutableMapOf<DocumentPath, String>()
        val documents = mutableMapOf<DocumentPath, DocumentNotation>()

        for (e in notationMedia.scan().documents.values) {
            val projectPath = e.key

            val body = notationMedia.readDocument(projectPath)

            documentBodies[projectPath] = body

            val documentNotation = notationParser.parseDocumentObjects(body)
            documents[projectPath] = DocumentNotation(documentNotation, e.value.resources)
        }

        return GraphNotation(DocumentPathMap(documents.toPersistentMap()))
    }


    suspend fun graphStructure(): GraphStructure {
        val graphNotation = graphNotation()
        return graphStructure(graphNotation)
    }


    private fun graphStructure(
            graphNotation: GraphNotation
    ): GraphStructure {
        val graphMetadata = notationMetadataReader.read(graphNotation)
        return GraphStructure(graphNotation, graphMetadata)
    }


    suspend fun graphDefinition(): GraphDefinition {
        val graphStructure = graphStructure()
        return graphDefinition(graphStructure)
    }


    private fun graphDefinition(
            graphNotation: GraphNotation
    ): GraphDefinition {
        val graphStructure = graphStructure(graphNotation)
        return graphDefinition(graphStructure)
    }


    private fun graphDefinition(
            graphStructure: GraphStructure
    ): GraphDefinition {
        return graphDefiner.define(graphStructure)
    }


    suspend fun digest(): Digest {
        return notationMedia.scan().digest()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(
            command: NotationCommand
    ): NotationEvent {
        @Suppress("UnnecessaryVariable")
        val notationEvent = applyInPlace(command)
//        publish(notationEvent)
        return notationEvent
    }


    private suspend fun applyInPlace(
            command: NotationCommand
    ): NotationEvent {
        val graphNotation = graphNotation()
//        val notationAggregate = NotationReducer(graphNotation)

//        val oldDocuments = graphNotation.documents

        val transition =
                when (command) {
                    is StructuralNotationCommand ->
                        notationReducer.apply(graphNotation, command)

                    is SemanticNotationCommand -> {
                        val graphDefinition = graphDefinition(graphNotation)
                        notationReducer.apply(graphDefinition, command)
                    }
                }

        val newGraphNotation = transition.graphNotation

//        var writtenAny = false
        for (updatedDocument in newGraphNotation.documents.values) {
            val oldDocument = graphNotation.documents.values[updatedDocument.key]

            /*val written =*/
            writeIfRequired(
                    updatedDocument.key,
                    updatedDocument.value,
                    command,
                    oldDocument)

//            writtenAny = writtenAny || written
        }

        val removedDocumentPaths = graphNotation.documents.values.keys.minus(
                newGraphNotation.documents.values.keys)

        for (removed in removedDocumentPaths) {
            delete(removed)
//            writtenAny = true
        }

//        if (writtenAny) {
//            // TODO: avoid needless clearing
//            refresh()
//        }

        return transition.notationEvent
    }


    private suspend fun writeIfRequired(
            documentPath: DocumentPath,
            documentNotation: DocumentNotation,
            command: NotationCommand,
            originalDocument: DocumentNotation?
    ): Boolean {
        if (originalDocument != null &&
                documentNotation.objects.equalsInOrder(originalDocument.objects) &&
                documentNotation.resources == originalDocument.resources) {
            return false
        }

//        val previousDocumentScan = scanCache[documentPath]
//        val previousDocumentScan = scanCache[documentPath]
//        val previousDigest = previousDocumentScan?.documentDigest
        val previouslyPresent = originalDocument != null

        val previousBody: String =
                if (previouslyPresent) {
                    notationMedia.readDocument(documentPath)
                }
                else {
                    ""
                }

        val updatedBody = notationParser.unparseDocument(documentNotation, previousBody)
//        println("!!! updatedBody: ${IoUtils.utf8ToString(updatedBody)}")

        var modified = false

        if (updatedBody != previousBody || ! previouslyPresent) {
            notationMedia.writeDocument(documentPath, updatedBody)
            modified = true
        }

        when (command) {
            is CopyDocumentCommand -> {
                val originalDocumentPath = command.sourceDocumentPath
                val originalResources = originalDocument?.resources!!

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

//        scanCache[documentPath] = DocumentScan(
//                Digest.ofUtf8(updatedBody),
//                documentNotation.resources)

        return modified
    }


    private suspend fun delete(
            documentPath: DocumentPath
    ) {
        notationMedia.deleteDocument(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun refresh() {
        notationMedia.invalidate()
        graphNotationCacheDigest = null
        graphNotationCache = null
    }
}