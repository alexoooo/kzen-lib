package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.DigestCache
import tech.kzen.lib.platform.collect.toPersistentMap


class DirectGraphStore(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser,
        private val notationMetadataReader: NotationMetadataReader,
        private val graphDefiner: GraphDefiner,
        private val notationReducer: NotationReducer
): LocalGraphStore {
    //-----------------------------------------------------------------------------------------------------------------
    private var graphNotationCacheDigest: Digest? = null
    private var graphNotationCache: GraphNotation? = null
    private val documentObjectCache = DigestCache<DocumentObjectNotation>(128)

    private val observers = mutableSetOf<LocalGraphStore.Observer>()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun observe(observer: LocalGraphStore.Observer) {
        observers.add(observer)

        val graphDefinition = graphDefinition()
        observer.onStoreRefresh(graphDefinition)
    }


    override fun unobserve(observer: LocalGraphStore.Observer) {
        observers.remove(observer)
    }


    private suspend fun publishSuccess(event: NotationEvent) {
        val graphDefinition = graphDefinition()

        for (observer in observers) {
            observer.onCommandSuccess(event, graphDefinition)
        }
    }


    private suspend fun publishFailure(command: NotationCommand, cause: Throwable) {
        for (observer in observers) {
            observer.onCommandFailure(command, cause)
        }
    }


    private suspend fun publishRefresh() {
        val graphDefinition = graphDefinition()

        for (observer in observers) {
            observer.onStoreRefresh(graphDefinition)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun graphNotation(): GraphNotation {
        val notationScan = notationMedia.scan()
        return graphNotation(notationScan)
    }


    private suspend fun graphNotation(notationScan: NotationScan): GraphNotation {
        val digest = notationScan.digest()

        if (graphNotationCacheDigest != digest) {
            graphNotationCacheDigest = digest
            graphNotationCache = graphNotationImpl(notationScan)
        }

        return graphNotationCache!!
    }


    private suspend fun graphNotationImpl(notationScan: NotationScan): GraphNotation {
        val documentBodies = mutableMapOf<DocumentPath, String>()
        val documents = mutableMapOf<DocumentPath, DocumentNotation>()

        for (e in notationScan.documents.values) {
            val projectPath = e.key

            val body = notationMedia.readDocument(projectPath, e.value.documentDigest)

            documentBodies[projectPath] = body

            val cachedDocumentObjectNotation = documentObjectCache.get(e.value.documentDigest)
            if (cachedDocumentObjectNotation != null) {
                documents[projectPath] = DocumentNotation(cachedDocumentObjectNotation, e.value.resources)
            }
            else {
                val documentObjectNotation = notationParser.parseDocumentObjects(body)
                documents[projectPath] = DocumentNotation(documentObjectNotation, e.value.resources)

                documentObjectCache.put(e.value.documentDigest, documentObjectNotation)
            }
        }

        return GraphNotation(DocumentPathMap(documents.toPersistentMap()))
    }


    override suspend fun graphStructure(): GraphStructure {
        val graphNotation = graphNotation()
        return graphStructure(graphNotation)
    }


    private fun graphStructure(
        graphNotation: GraphNotation
    ): GraphStructure {
        val graphMetadata = notationMetadataReader.read(graphNotation)
        return GraphStructure(graphNotation, graphMetadata)
    }


    override suspend fun graphDefinition(): GraphDefinitionAttempt {
        val graphStructure = graphStructure()
        return graphDefinition(graphStructure)
    }


    private fun graphDefinition(
            graphNotation: GraphNotation
    ): GraphDefinitionAttempt {
        val graphStructure = graphStructure(graphNotation)
        return graphDefinition(graphStructure)
    }


    private fun graphDefinition(
        graphStructure: GraphStructure
    ): GraphDefinitionAttempt {
        return graphDefiner.tryDefine(graphStructure)
    }


    suspend fun digest(): Digest {
        return notationMedia.scan().digest()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(
        command: NotationCommand
    ): NotationEvent {
        val notationEvent = applyInPlace(command)
        publishSuccess(notationEvent)
        return notationEvent
    }


    private suspend fun applyInPlace(
        command: NotationCommand
    ): NotationEvent {
        val notationScan = notationMedia.scan()
        val graphNotation = graphNotation(notationScan)

        val transition =
                when (command) {
                    is StructuralNotationCommand ->
                        notationReducer.applyStructural(graphNotation, command)

                    is SemanticNotationCommand -> {
                        val graphDefinitionAttempt = graphDefinition(graphNotation)
                        notationReducer.applySemantic(graphDefinitionAttempt, command)
                    }
                }

        val newGraphNotation = transition.graphNotation

        writeModified(graphNotation, newGraphNotation, command, transition, notationScan)

        val removedDocumentPaths = graphNotation.documents.values.keys
                .minus(newGraphNotation.documents.values.keys)

        for (removed in removedDocumentPaths) {
            delete(removed)
        }

        return transition.notationEvent
    }


    private suspend fun writeModified(
            oldGraphNotation: GraphNotation,
            newGraphNotation: GraphNotation,
            command: NotationCommand,
            transition: NotationTransition,
            oldNotationScan: NotationScan
    ) {
        val copiedDocumentEvents =
                (transition.notationEvent as? CompoundNotationEvent)
                        ?.singularEvents
                        ?.filterIsInstance<CopiedDocumentEvent>()
                        ?: listOf()

        for (updatedDocument in newGraphNotation.documents.values) {
            val oldDocument = oldGraphNotation.documents.values[updatedDocument.key]

            val copiedDocumentEvent =
                    if (oldDocument != null) {
                        null
                    }
                    else {
                        copiedDocumentEvents.find { it.destination == updatedDocument.key }
                    }

            if (copiedDocumentEvent != null) {
                val oldDocumentScan = oldNotationScan.documents[copiedDocumentEvent.documentPath]

                writeCopy(
                        oldGraphNotation,
                        copiedDocumentEvent,
                        oldDocumentScan?.documentDigest)
            }
            else {
                val oldDocumentScan = oldNotationScan.documents[updatedDocument.key]

                writeIfRequired(
                        updatedDocument.key,
                        updatedDocument.value,
                        command,
                        oldDocument,
                        oldDocumentScan?.documentDigest)
            }
        }
    }


    private suspend fun writeCopy(
            oldGraphNotation: GraphNotation,
            copiedDocumentEvent: CopiedDocumentEvent,
            originalDocumentDigest: Digest?
    ) {
        val sourceDocumentPath = copiedDocumentEvent.documentPath
        val destinationDocumentPath = copiedDocumentEvent.destination

        val sourceDocumentContents = notationMedia
                .readDocument(sourceDocumentPath, originalDocumentDigest)

        notationMedia.writeDocument(destinationDocumentPath, sourceDocumentContents)

        val sourceDocument = oldGraphNotation.documents[sourceDocumentPath]!!
        if (sourceDocument.resources != null) {
            for (resourcePath in sourceDocument.resources.digests.keys) {
                notationMedia.copyResource(
                        ResourceLocation(sourceDocumentPath, resourcePath),
                        ResourceLocation(destinationDocumentPath, resourcePath))
            }
        }
    }


    private suspend fun writeIfRequired(
            documentPath: DocumentPath,
            documentNotation: DocumentNotation,
            command: NotationCommand,
            originalDocument: DocumentNotation?,
            originalDocumentDigest: Digest?
    ) {
        if (documentNotation == originalDocument) {
            return
        }

        val previouslyPresent = originalDocument != null

        val previousBody: String =
                if (previouslyPresent) {
                    notationMedia.readDocument(documentPath, originalDocumentDigest)
                }
                else {
                    ""
                }

        val updatedBody = notationParser.unparseDocument(documentNotation.objects, previousBody)
//        println("!!! updatedBody: ${IoUtils.utf8ToString(updatedBody)}")

        if (updatedBody != previousBody || ! previouslyPresent) {
            notationMedia.writeDocument(documentPath, updatedBody)
        }

        when (command) {
//            is CopyDocumentCommand -> {
//                val originalDocumentPath = command.sourceDocumentPath
//                val originalResources = originalDocument?.resources!!
//
//                for (resourcePath in originalResources.digests.keys) {
//                    val contents = notationMedia.readResource(
//                            ResourceLocation(originalDocumentPath, resourcePath))
//
//                    notationMedia.writeResource(
//                            ResourceLocation(documentPath, resourcePath),
//                            contents)
//                }
//            }

            is ResourceNotationCommand -> {
                when (command) {
                    is AddResourceCommand -> {
                        notationMedia.writeResource(
                                command.resourceLocation,
                                command.resourceContent)
                    }

                    is RemoveResourceCommand -> {
                        notationMedia.deleteResource(command.resourceLocation)
                    }
                }
            }

            else -> {}
        }
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