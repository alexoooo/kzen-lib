package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectNestingSegment
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentList


object NotationReducer {
    //-----------------------------------------------------------------------------------------------------------------
    private class StructuralBuffer(
        var graphNotation: GraphNotation
    ) {
        fun apply(
            structuralNotationCommand: StructuralNotationCommand
        ): NotationEvent {
            val transition = applyStructural(graphNotation, structuralNotationCommand)
            graphNotation = transition.graphNotation
            return transition.notationEvent
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun applyStructural(
        graphNotation: GraphNotation,
        structuralNotationCommand: StructuralNotationCommand
    ): NotationTransition {
        return when (structuralNotationCommand) {
            is CreateDocumentCommand ->
                createDocument(graphNotation, structuralNotationCommand)

            is DeleteDocumentCommand ->
                deleteDocument(graphNotation, structuralNotationCommand)

            is CreateFolderCommand ->
                createFolder(graphNotation, structuralNotationCommand)

            is DeleteFolderCommand ->
                deleteFolder(graphNotation, structuralNotationCommand)

            is CopyDocumentCommand ->
                copyDocument(graphNotation, structuralNotationCommand)

            is SetDocumentObjectsCommand ->
                setDocumentObjects(graphNotation, structuralNotationCommand)


            is AddObjectCommand ->
                addObject(graphNotation, structuralNotationCommand)

            is RemoveObjectCommand ->
                removeObject(graphNotation, structuralNotationCommand)

            is ShiftObjectCommand ->
                shiftObject(graphNotation, structuralNotationCommand)

            is ShiftObjectTreeCommand ->
                shiftObjectTree(graphNotation, structuralNotationCommand)

            is RenameObjectCommand ->
                renameObject(graphNotation, structuralNotationCommand)

            is RenameNestedObjectCommand ->
                renameNestedObject(graphNotation, structuralNotationCommand)


            is UpsertAttributeCommand ->
                upsertAttribute(graphNotation, structuralNotationCommand)

            is UpdateInAttributeCommand ->
                updateInAttribute(graphNotation, structuralNotationCommand)

            is UpdateAllNestingsInAttributeCommand ->
                updateAllNestingsInAttribute(graphNotation, structuralNotationCommand)

            is UpdateAllValuesInAttributeCommand ->
                updateAllValuesInAttribute(graphNotation, structuralNotationCommand)

            is InsertListItemInAttributeCommand ->
                insertListItemInAttribute(graphNotation, structuralNotationCommand)

            is InsertAllListItemsInAttributeCommand ->
                insertAllListItemsInAttribute(graphNotation, structuralNotationCommand)

            is InsertMapEntryInAttributeCommand ->
                insertMapEntryInAttribute(graphNotation, structuralNotationCommand)

            is RemoveInAttributeCommand ->
                removeInAttribute(graphNotation, structuralNotationCommand)

            is RemoveListItemInAttributeCommand ->
                removeListItemInAttribute(graphNotation, structuralNotationCommand)

            is RemoveAllListItemsInAttributeCommand ->
                removeAllListItemsInAttribute(graphNotation, structuralNotationCommand)


            is ShiftInAttributeCommand ->
                shiftInAttribute(graphNotation, structuralNotationCommand)

            is AddObjectAtAttributeCommand ->
                addObjectAtAttribute(graphNotation, structuralNotationCommand)

            is InsertObjectInListAttributeCommand ->
                insertObjectInListAttribute(graphNotation, structuralNotationCommand)

            is RemoveObjectInAttributeCommand ->
                removeObjectInAttribute(graphNotation, structuralNotationCommand)


            is AddResourceCommand ->
                addResource(graphNotation, structuralNotationCommand)

            is RemoveResourceCommand ->
                removeResource(graphNotation, structuralNotationCommand)


//            else ->
//                throw UnsupportedOperationException("Unknown command: $command")
        }
    }


    fun applySemantic(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        semanticNotationCommand: SemanticNotationCommand
    ): NotationTransition {
        return when (semanticNotationCommand) {
            is RenameObjectRefactorCommand ->
                renameObjectRefactor(
                    graphDefinitionAttempt, semanticNotationCommand.objectLocation, semanticNotationCommand.newName)

            is RenameDocumentRefactorCommand ->
                relocateDocumentRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.withName(semanticNotationCommand.newName))

            is MoveDocumentRefactorCommand ->
                relocateDocumentRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.copy(nesting = semanticNotationCommand.newNesting))

            is RenameFolderRefactorCommand ->
                relocateFolderRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.withName(semanticNotationCommand.newName))

            is MoveFolderRefactorCommand ->
                relocateFolderRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.copy(nesting = semanticNotationCommand.newNesting))

            is RelocateObjectTreeRefactorCommand ->
                relocateObjectTreeRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.objectLocation,
                    semanticNotationCommand.newObjectNesting,
                    semanticNotationCommand.newPositionInDocument)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun createDocument(
        state: GraphNotation,
        command: CreateDocumentCommand
    ): NotationTransition {
        check(!command.documentPath.folder) {
            "Cannot create a document at a folder path (use CreateFolderCommand): ${command.documentPath}"
        }
        check(!state.documents.map.containsKey(command.documentPath)) {
            "Already exists: ${command.documentPath}"
        }

        val documentNotation = DocumentNotation.ofObjectsWithEmptyOrNullResources(
                command.documentObjectNotation, command.documentPath.directory)

        val nextState = state.withNewDocument(
                command.documentPath, documentNotation)

        val event = CreatedDocumentEvent(
                command.documentPath, command.documentObjectNotation)

        return NotationTransition(event, nextState)
    }


    private fun deleteDocument(
        state: GraphNotation,
        command: DeleteDocumentCommand
    ): NotationTransition {
        check(state.documents.map.containsKey(command.documentPath)) {
            "Does not exist: ${command.documentPath} - ${state.documents.map.keys}"
        }

        val nextState = state.withoutDocument(command.documentPath)

        return NotationTransition(
                DeletedDocumentEvent(command.documentPath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun createFolder(
        state: GraphNotation,
        command: CreateFolderCommand
    ): NotationTransition {
        check(command.documentPath.folder) {
            "Not a folder path: ${command.documentPath}"
        }
        check(!state.documents.map.containsKey(command.documentPath)) {
            "Already exists: ${command.documentPath}"
        }

        val nextState = state.withNewDocument(
            command.documentPath, DocumentNotation.folder)

        return NotationTransition(
            CreatedFolderEvent(command.documentPath),
            nextState)
    }


    private fun deleteFolder(
        state: GraphNotation,
        command: DeleteFolderCommand
    ): NotationTransition {
        check(command.documentPath.folder) {
            "Not a folder path: ${command.documentPath}"
        }

        // Cascade: remove the folder's own entry plus every document/folder nested under its content nesting
        // (folder "foo" at nesting N holds its contents at N + foo). Every folder has its own entry, so removing
        // the whole subtree here puts the folder directory itself into the store's removed set — the generic
        // deepest-first delete loop then drops the directory with no folder-specific special-casing.
        val contentNesting = command.documentPath.nesting.plus(
            DocumentSegment(command.documentPath.name.value))

        val toRemove = state.documents.map.keys.filter { path ->
            path == command.documentPath || path.nesting.startsWith(contentNesting)
        }

        check(toRemove.isNotEmpty()) {
            "Empty or unknown folder: ${command.documentPath} - ${state.documents.map.keys}"
        }

        var nextState = state
        for (path in toRemove) {
            nextState = nextState.withoutDocument(path)
        }

        return NotationTransition(
            DeletedFolderEvent(command.documentPath),
            nextState)
    }


    private fun copyDocument(
        state: GraphNotation,
        command: CopyDocumentCommand
    ): NotationTransition {
        check(command.sourceDocumentPath in state.documents.map) {
            "Does not exist: ${command.sourceDocumentPath} - ${state.documents.map.keys}"
        }

        val document = state.documents[command.sourceDocumentPath]!!

        val withDocumentNotationCopy = state
                .withNewDocument(command.destinationDocumentPath, document)

        return NotationTransition(
                CopiedDocumentEvent(command.sourceDocumentPath, command.destinationDocumentPath),
                withDocumentNotationCopy)
    }


    private fun setDocumentObjects(
        state: GraphNotation,
        command: SetDocumentObjectsCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.documentPath]
        checkNotNull(documentNotation) {
            "Does not exist: ${command.documentPath} - ${state.documents.map.keys}"
        }

        val modifiedDocumentNotation = documentNotation.withObjects(command.documentObjectNotation)

        val nextState = state.withModifiedDocument(
            command.documentPath, modifiedDocumentNotation)

        val event = SetDocumentObjectsEvent(
            command.documentPath, command.documentObjectNotation)

        return NotationTransition(event, nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
        graphNotation: GraphNotation,
        command: AddObjectCommand
    ): NotationTransition {
        check(command.objectLocation !in graphNotation.coalesce.map) {
            "Object named '${command.objectLocation}' already exists"
        }

        val documentNotation = graphNotation.documents.map[command.objectLocation.documentPath]!!

        val indexInDocument =
            command.indexInDocument.resolve(documentNotation.objects.notations.map.size)

        val modifiedDocumentNotation =
                documentNotation.withNewObject(
                    PositionedObjectPath(command.objectLocation.objectPath, indexInDocument),
                    command.body)

        val nextState = graphNotation.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
                AddedObjectEvent(
                    command.objectLocation,
                    indexInDocument,
                    command.body),
                nextState)
    }


    private fun removeObject(
        state: GraphNotation,
        command: RemoveObjectCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.map)

        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

        val modifiedDocumentNotation =
                documentNotation.withoutObject(command.objectLocation.objectPath)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
                RemovedObjectEvent(command.objectLocation),
                nextState)
    }


    private fun shiftObject(
        state: GraphNotation,
        command: ShiftObjectCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.map)

        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

        val objectNotation = state.coalesce[command.objectLocation]!!

        val removedFromCurrent = documentNotation.withoutObject(command.objectLocation.objectPath)

        val newPositionInDocument =
            command.newPositionInDocument.resolve(documentNotation.objects.notations.map.size)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, newPositionInDocument),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedToNew)

        return NotationTransition(
                ShiftedObjectEvent(command.objectLocation, newPositionInDocument),
                nextState)
    }


    private fun shiftObjectTree(
        state: GraphNotation,
        command: ShiftObjectTreeCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.map)

        val documentPath = command.objectLocation.documentPath
        val documentNotation = state.documents.map[documentPath]!!
        val rootObjectPath = command.objectLocation.objectPath

        // The subtree: root + every descendant object, captured in current document order.
        val subtree = documentNotation.objects.notations.map.entries
            .filter { it.key == rootObjectPath || it.key.startsWith(rootObjectPath) }
            .map { it.key to it.value }

        var remaining = documentNotation
        for ((objectPath, _) in subtree) {
            remaining = remaining.withoutObject(objectPath)
        }

        val rootPositionInDocument = command.newPositionInDocument.resolve(
            remaining.objects.notations.map.size)

        var rebuilt = remaining
        for ((index, entry) in subtree.withIndex()) {
            rebuilt = rebuilt.withNewObject(
                PositionedObjectPath(entry.first, PositionIndex(rootPositionInDocument.value + index)),
                entry.second)
        }

        val nextState = state.withModifiedDocument(documentPath, rebuilt)

        return NotationTransition(
                ShiftedObjectTreeEvent(command.objectLocation, rootPositionInDocument),
                nextState)
    }


    private fun renameObject(
        state: GraphNotation,
        command: RenameObjectCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.map)

        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!
        val objectIndex = documentNotation.indexOf(command.objectLocation.objectPath)

        val removedCurrentName =
                documentNotation.withoutObject(command.objectLocation.objectPath)

        val newObjectPath = command.objectLocation.objectPath.copy(name = command.newName)

        val addedWithNewName = removedCurrentName.withNewObject(
                PositionedObjectPath(newObjectPath, objectIndex),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedWithNewName)

        return NotationTransition(
                RenamedObjectEvent(command.objectLocation, command.newName),
                nextState)
    }


    private fun renameNestedObject(
        state: GraphNotation,
        command: RenameNestedObjectCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.map)

        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!
        val objectIndex = documentNotation.indexOf(command.objectLocation.objectPath)

        val removedCurrentNesting =
                documentNotation.withoutObject(command.objectLocation.objectPath)

        val newObjectPath = command.objectLocation.objectPath.copy(nesting = command.newObjectNesting)

        val addedWithNewNesting = removedCurrentNesting.withNewObject(
                PositionedObjectPath(newObjectPath, objectIndex),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedWithNewNesting)

        return NotationTransition(
                RenamedNestedObjectEvent(
                        command.objectLocation, command.newObjectNesting),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun upsertAttribute(
        state: GraphNotation,
        command: UpsertAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]
                ?: throw IllegalArgumentException("Unknown document path: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Unknown object location: ${command.objectLocation}")

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofName(command.attributeName), command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = UpsertedAttributeEvent(
            command.objectLocation, command.attributeName, command.attributeNotation)

        return NotationTransition(event, nextState)
    }


    private fun updateInAttribute(
        state: GraphNotation,
        command: UpdateInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state
            .mergeAttribute(command.objectLocation, command.attributePath.attribute)
            ?: throw IllegalArgumentException(
                "Not found: ${command.objectLocation} - ${command.attributePath.attribute}")

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.attributePath.attribute, mergedAttributeNotation)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = UpdatedInAttributeEvent(
            command.objectLocation, command.attributePath, command.attributeNotation)

        return NotationTransition(event, nextState)
    }


    private fun updateAllNestingsInAttribute(
        state: GraphNotation,
        command: UpdateAllNestingsInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state
            .mergeAttribute(command.objectLocation, command.attributeName)
            ?: throw IllegalArgumentException(
                "Not found: ${command.objectLocation} - ${command.attributeName}")

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.attributeName, mergedAttributeNotation)

        var modifiedObjectNotation = objectWithMergedAttribute
        for (attributeNesting in command.attributeNestings) {
            modifiedObjectNotation =  modifiedObjectNotation.upsertAttribute(
                AttributePath(command.attributeName, attributeNesting),
                command.attributeNotation)
        }

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = UpdatedAllNestingsInAttributeEvent(
            command.objectLocation, command.attributeName, command.attributeNestings, command.attributeNotation)

        return NotationTransition(event, nextState)
    }


    private fun updateAllValuesInAttribute(
        state: GraphNotation,
        command: UpdateAllValuesInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state
            .mergeAttribute(command.objectLocation, command.attributeName)
            ?: throw IllegalArgumentException(
                "Not found: ${command.objectLocation} - ${command.attributeName}")

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.attributeName, mergedAttributeNotation)

        var modifiedObjectNotation = objectWithMergedAttribute
        for ((nesting, notation) in command.nestingNotations) {
            modifiedObjectNotation =  modifiedObjectNotation.upsertAttribute(
                AttributePath(command.attributeName, nesting),
                notation)
        }

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = UpdatedAllValuesInAttributeEvent(
            command.objectLocation, command.attributeName, command.nestingNotations)

        return NotationTransition(event, nextState)
    }


    private fun insertListItemInAttribute(
        state: GraphNotation,
        command: InsertListItemInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state
            .mergeAttribute(command.objectLocation, command.containingList.attribute)
            ?: throw IllegalArgumentException(
                "Not found: ${command.objectLocation} - ${command.containingList.attribute}")

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.containingList.attribute, mergedAttributeNotation)

        val listInAttribute = objectWithMergedAttribute
            .get(command.containingList) as? ListAttributeNotation
            ?: throw IllegalStateException(
                "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insert(indexInList, command.item)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
                command.containingList, listWithInsert)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedListItemInAttributeEvent(
                command.objectLocation, command.containingList, indexInList, listInAttribute)

        return NotationTransition(event, nextState)
    }


    private fun insertAllListItemsInAttribute(
        state: GraphNotation,
        command: InsertAllListItemsInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state
            .mergeAttribute(command.objectLocation, command.containingList.attribute)
            ?: throw IllegalArgumentException(
                "Not found: ${command.objectLocation} - ${command.containingList.attribute}")

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.containingList.attribute, mergedAttributeNotation)

        val listInAttribute = objectWithMergedAttribute
            .get(command.containingList) as? ListAttributeNotation
            ?: throw IllegalStateException(
                "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insertAll(indexInList, command.items)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
            command.containingList, listWithInsert)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedAllListItemsInAttributeEvent(
            command.objectLocation, command.containingList, indexInList, command.items)

        return NotationTransition(event, nextState)
    }


    private fun insertMapEntryInAttribute(
        state: GraphNotation,
        command: InsertMapEntryInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containingAttribute = objectNotation.get(command.containingMap)

        require(containingAttribute == null || containingAttribute is MapAttributeNotation) {
            "Map expected: ${command.containingMap} - $containingAttribute"
        }

        val containingMapExists = containingAttribute != null

        val containingMapSize = containingAttribute?.map?.size ?: 0
        val indexInMap = command.indexInMap.resolve(containingMapSize)

        val createdAncestors = mutableListOf<AttributePath>()

        val modifiedObjectNotation =
            if (!containingMapExists) {
                require(command.createAncestorsIfAbsent) {
                    "Containing map missing: ${command.containingMap}"
                }

                require(indexInMap.value == 0) {
                    "Index out of bounds in empty map: ${command.indexInMap}"
                }

                val containerNotation = MapAttributeNotation(persistentMapOf(
                    command.mapKey to command.value
                ))

                var missingAncestorChain = containerNotation

                var furthestPresentAncestor: AttributePath? = command.containingMap
                while (objectNotation.get(furthestPresentAncestor!!) == null) {
                    createdAncestors.add(furthestPresentAncestor)

                    if (furthestPresentAncestor.nesting.segments.isEmpty()) {
                        furthestPresentAncestor = null
                        break
                    }

                    val missingKey = furthestPresentAncestor.nesting.segments.last()
                    missingAncestorChain = MapAttributeNotation(persistentMapOf(
                        missingKey to missingAncestorChain
                    ))

                    furthestPresentAncestor = furthestPresentAncestor.parent()
                }

                @Suppress("SENSELESS_COMPARISON")
                if (furthestPresentAncestor == null) {
                    objectNotation.upsertAttribute(command.containingMap.attribute, missingAncestorChain)
                }
                else {
                    val presentAncestorNotation = objectNotation.get(furthestPresentAncestor)
                    require(presentAncestorNotation is MapAttributeNotation) {
                        "Map expected: $presentAncestorNotation"
                    }
                    val keyInPresentAncestor = missingAncestorChain.map.keys.first()
                    val notionUnderPresentAncestor = missingAncestorChain.map[keyInPresentAncestor]!!

                    objectNotation.upsertAttribute(
                        furthestPresentAncestor,
                        presentAncestorNotation.put(keyInPresentAncestor, notionUnderPresentAncestor))
                }
            }
            else {
                val mapWithInsert = containingAttribute.insert(
                    command.value, command.mapKey, indexInMap)

                objectNotation.upsertAttribute(
                    command.containingMap, mapWithInsert)
            }

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedMapEntryInAttributeEvent(
            command.objectLocation,
            command.containingMap,
            indexInMap,
            command.mapKey,
            command.value,
            createdAncestors.reversed())

        return NotationTransition(event, nextState)
    }


    private fun removeInAttribute(
        state: GraphNotation,
        command: RemoveInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.attributePath.parent()
        val containerNotation = objectNotation.get(containerPath)
                as? StructuredAttributeNotation
            ?: throw IllegalArgumentException("Structured container expected: " +
                    "$containerPath - ${objectNotation.get(containerPath)}")

        val lastSegment = command.attributePath.nesting.segments.last()

        val containerWithoutElement =
                when (containerNotation) {
                    is ListAttributeNotation -> {
                        val parsedIndex = PositionIndex(lastSegment.asIndex()!!)
                        containerNotation.remove(parsedIndex)
                    }

                    is MapAttributeNotation -> {
                        containerNotation.remove(lastSegment)
                    }
                }

        val modifiedObjectNotation =
            if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
                removeEmptyContainer(objectNotation, containerPath)
            }
            else {
                objectNotation.upsertAttribute(
                    containerPath, containerWithoutElement)
            }

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = RemovedInAttributeEvent(
                command.objectLocation, command.attributePath)

        return NotationTransition(event, nextState)
    }


    private fun removeListItemInAttribute(
        state: GraphNotation,
        command: RemoveListItemInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.containingList
        val containerNotation = objectNotation.get(containerPath)
                as? ListAttributeNotation
            ?: throw IllegalArgumentException("List expected: " +
                    "$containerPath - ${objectNotation.get(containerPath)}")

        val firstIndex = containerNotation.values.indexOfFirst { it == command.item }
        require(firstIndex != -1) { "List does not contain item: ${command.item} - $containerNotation" }

        val lastIndex = containerNotation.values.indexOfLast { it == command.item }
        require(firstIndex == lastIndex) {
            "List contains item duplicates: ${command.item} - $containerNotation"
        }

        val itemIndex = PositionIndex(firstIndex)
        val containerWithoutElement = containerNotation.remove(itemIndex)

        val modifiedObjectNotation =
            if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
                removeEmptyContainer(objectNotation, containerPath)
            }
            else {
                objectNotation.upsertAttribute(
                    containerPath, containerWithoutElement)
            }

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val removedAttributePath = command.containingList.nest(
            AttributeSegment.ofIndex(firstIndex))

        val event = RemovedInAttributeEvent(
                command.objectLocation, removedAttributePath)

        return NotationTransition(event, nextState)
    }


    private fun removeAllListItemsInAttribute(
        state: GraphNotation,
        command: RemoveAllListItemsInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.containingList
        val containerNotation = objectNotation.get(containerPath)
                as? ListAttributeNotation
            ?: throw IllegalArgumentException("List expected: " +
                    "$containerPath - ${objectNotation.get(containerPath)}")

        val removedAttributePaths = mutableListOf<AttributePath>()
        var nextObjectNotation = objectNotation
        var nextContainerNotation = containerNotation

        for (item in command.items) {
            val firstIndex = nextContainerNotation.values.indexOfFirst { it == item }
            require(firstIndex != -1) { "List does not contain item: $item - $nextContainerNotation" }

            val lastIndex = nextContainerNotation.values.indexOfLast { it == item }
            require(firstIndex == lastIndex) {
                "List contains item duplicates: $item - $nextContainerNotation"
            }

            val itemIndex = PositionIndex(firstIndex)
            val containerWithoutElement = nextContainerNotation.remove(itemIndex)

            val modifiedObjectNotation =
                if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
                    removeEmptyContainer(nextObjectNotation, containerPath)
                }
                else {
                    nextObjectNotation.upsertAttribute(
                        containerPath, containerWithoutElement)
                }

            val removedAttributePath = command.containingList.nest(
                AttributeSegment.ofIndex(firstIndex))

            removedAttributePaths.add(removedAttributePath)

            nextContainerNotation = containerWithoutElement
            nextObjectNotation = modifiedObjectNotation
        }

        val event = RemovedAllInAttributeEvent(
            command.objectLocation, removedAttributePaths)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, nextObjectNotation)

        val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(event, nextState)
    }


    private fun removeEmptyContainer(
        objectNotation: ObjectNotation,
        containerPath: AttributePath
    ): ObjectNotation {
        val containerParent = containerPath.parent()
        val parentNotion = objectNotation.get(containerParent)
                as StructuredAttributeNotation

        if (containerPath.nesting.segments.isEmpty()) {
            return objectNotation.removeAttribute(containerPath.attribute)
        }

        val containerSegment = containerPath.nesting.segments.last()

        val parentWithoutContainer =
            when (parentNotion) {
                is MapAttributeNotation -> {
                    parentNotion.remove(containerSegment)
                }

                is ListAttributeNotation -> {
                    parentNotion.remove(PositionIndex(containerSegment.asIndex()!!))
                }
            }

        return objectNotation.upsertAttribute(
            containerParent, parentWithoutContainer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun shiftInAttribute(
        state: GraphNotation,
        command: ShiftInAttributeCommand
    ): NotationTransition {
        val objectNotation = state.coalesce[command.objectLocation]
            ?: throw IllegalArgumentException("Object location not found: $command")

        val containerPath = command.attributePath.parent()
        val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

        val attributeNotation = objectNotation.get(command.attributePath)
            ?: throw IllegalArgumentException("Attribute path not found: $command")

        val builder = StructuralBuffer(state)

        val removedInAttribute = builder
            .apply(RemoveInAttributeCommand(
                command.objectLocation,
                command.attributePath,
                false
            )) as RemovedInAttributeEvent

        val insertCommand = when (containerNotation) {
            is ListAttributeNotation ->
                InsertListItemInAttributeCommand(
                    command.objectLocation,
                    containerPath,
                    command.newPosition,
                    attributeNotation)

            is MapAttributeNotation ->
                InsertMapEntryInAttributeCommand(
                    command.objectLocation,
                    containerPath,
                    command.newPosition,
                    command.attributePath.nesting.segments.last(),
                    attributeNotation,
                    false)
        }

        val reinsertedInAttribute = builder
            .apply(insertCommand)
            as InsertedInAttributeEvent

        return NotationTransition(
            ShiftedInAttributeEvent(removedInAttribute, reinsertedInAttribute),
            builder.graphNotation)
    }


    private fun addObjectAtAttribute(
        graphNotation: GraphNotation,
        command: AddObjectAtAttributeCommand
    ): NotationTransition {
        val buffer = StructuralBuffer(graphNotation)

        val objectLocation = command.insertedObjectLocation()

        val objectAdded = buffer
            .apply(AddObjectCommand(
                objectLocation,
                command.positionInDocument,
                command.objectNotation))
            as AddedObjectEvent

        val addendReference = objectLocation.toReference()
            .crop(retainPath = false)

//        val existingValue = graphNotation.getString(
//            command.containingObjectLocation, AttributePath.ofName(command.containingAttribute))

        val insertAtAttributeCommand = UpsertAttributeCommand(
            command.containingObjectLocation,
            command.containingAttribute,
            ScalarAttributeNotation(addendReference.asString()))

        val addedAtAttribute = buffer
            .apply(insertAtAttributeCommand)
            as UpsertedAttributeEvent

        return NotationTransition(
            AddedObjectAtAttributeEvent(objectAdded, addedAtAttribute),
                buffer.graphNotation)
    }


    private fun insertObjectInListAttribute(
        graphNotation: GraphNotation,
        command: InsertObjectInListAttributeCommand
    ): NotationTransition {
        val buffer = StructuralBuffer(graphNotation)

        val objectLocation = command.insertedObjectLocation()

        val objectAdded = buffer
            .apply(AddObjectCommand(
                objectLocation,
                command.positionInDocument,
                command.objectNotation))
            as AddedObjectEvent

        val addendReference = objectLocation.toReference()
            .crop(retainPath = false)

        val insertInAttributeCommand = InsertListItemInAttributeCommand(
            command.containingObjectLocation,
            command.containingList,
            command.indexInList,
            ScalarAttributeNotation(addendReference.asString()))

        val insertedInAttribute = buffer
            .apply(insertInAttributeCommand)
            as InsertedListItemInAttributeEvent

        return NotationTransition(
            InsertedObjectInListAttributeEvent(objectAdded, insertedInAttribute),
            buffer.graphNotation)
    }


//    private fun insertObjectInListAttributeWithInit(
//        graphDefinitionAttempt: GraphDefinitionAttempt,
//        command: InsertObjectInListAttributeCommand
//    ): NotationTransition {
//        val objectLocation = command.insertedObjectLocation()
//
//        val addObjectCommand = AddObjectCommand(
//            objectLocation,
//            command.positionInDocument,
//            command.objectNotation)
//        val objectAddedTransition = applySemantic(graphDefinitionAttempt, addObjectCommand)
//        val objectAddedEvent = objectAddedTransition.notationEvent as AddedObjectEvent
//
//        val addendReference = objectLocation.toReference().crop(retainPath = false)
//        val insertListItemInAttributeCommand = InsertListItemInAttributeCommand(
//            command.containingObjectLocation,
//            command.containingList,
//            command.indexInList,
//            ScalarAttributeNotation(addendReference.asString()))
//        val insertedInAttributeTransition = applyStructural(
//            objectAddedTransition.graphNotation, insertListItemInAttributeCommand)
//        val insertedInAttributeEvent = insertedInAttributeTransition.notationEvent as InsertedListItemInAttributeEvent
//
//        return NotationTransition(
//            InsertedObjectInListAttributeEvent(objectAddedEvent, insertedInAttributeEvent),
//            insertedInAttributeTransition.graphNotation)
//    }


    private fun removeObjectInAttribute(
        state: GraphNotation,
        command: RemoveObjectInAttributeCommand
    ): NotationTransition {
        val objectNotation = state.coalesce[command.containingObjectLocation]
            ?: throw IllegalArgumentException("Containing object not found: ${command.containingObjectLocation}")

        val attributeNotation = objectNotation.get(command.attributePath)!!
        val objectReference = ObjectReference.parse(attributeNotation.asString()!!)
        val objectReferenceHost = ObjectReferenceHost.ofLocation(command.containingObjectLocation)
        val objectLocation = state.coalesce.locate(objectReference, objectReferenceHost)

        val buffer = StructuralBuffer(state)

        val removedInAttribute = buffer
            .apply(RemoveInAttributeCommand(
                command.containingObjectLocation,
                command.attributePath,
                false
            )) as RemovedInAttributeEvent

        val removedObject = buffer
            .apply(RemoveObjectCommand(
                objectLocation
            )) as RemovedObjectEvent

        val containingDocumentPath = command.containingObjectLocation.documentPath

        val nestedObjectLocations = buffer
                .graphNotation
                .documents[containingDocumentPath]!!
                .objects
                .notations
                .map
                .keys
                .filter { it.startsWith(objectLocation.objectPath) }
                .toList()

        val removedNestedObjects = nestedObjectLocations
            .map {
                buffer.apply(RemoveObjectCommand(
                    ObjectLocation(containingDocumentPath, it)
                )) as RemovedObjectEvent
            }

        return NotationTransition(
            RemovedObjectInAttributeEvent(
                removedInAttribute, removedObject, removedNestedObjects),
            buffer.graphNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renameObjectRefactor(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        objectLocation: ObjectLocation,
        newName: ObjectName
    ): NotationTransition {
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
        check(objectLocation in graphNotation.coalesce.map)

        val buffer = StructuralBuffer(graphNotation)

        val nestedObjectLocations = graphNotation
            .documents[objectLocation.documentPath]!!
            .objects
            .notations
            .map
            .keys
            .filter { it.startsWith(objectLocation.objectPath) }
            .associateWith { renameNestedObject(objectLocation, newName, it) }

        val nestedObjects = nestedObjectLocations.map {
            nestedRenameObjectRefactor(
                ObjectLocation(objectLocation.documentPath, it.key),
                it.value.nesting,
                buffer,
                graphDefinitionAttempt
            )
        }

        val newObjectPath = objectLocation.objectPath.copy(name = newName)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
            objectLocation, newObjectLocation, graphDefinitionAttempt)

        val adjustedReferenceEvents = adjustedReferenceCommands
            .map { buffer.apply(it) as UpdatedInAttributeEvent }
            .toList()

        val renamedObject = buffer
            .apply(RenameObjectCommand(objectLocation, newName))
            as RenamedObjectEvent

        return NotationTransition(
            RenamedObjectRefactorEvent(
                renamedObject,
                adjustedReferenceEvents,
                nestedObjects),
            buffer.graphNotation)
    }


    private fun relocateObjectTreeRefactor(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        objectLocation: ObjectLocation,
        newObjectNesting: ObjectNesting,
        newPositionInDocument: PositionRelation
    ): NotationTransition {
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
        check(objectLocation in graphNotation.coalesce.map)

        val documentPath = objectLocation.documentPath
        val oldRootPath = objectLocation.objectPath
        val newRootPath = oldRootPath.copy(nesting = newObjectNesting)

        // Reject re-parenting an object into its own subtree (e.g. an If into its own Then branch);
        // startsWith catches any descendant destination, the equality check the no-op case.
        require(newRootPath != oldRootPath && ! newRootPath.startsWith(oldRootPath)) {
            "Cannot relocate an object into its own subtree: $oldRootPath -> $newRootPath"
        }

        val oldNestingSize = oldRootPath.nesting.segments.size

        // Root + every descendant, in current document order.
        val subtreePaths = graphNotation
            .documents[documentPath]!!
            .objects
            .notations
            .map
            .keys
            .filter { it == oldRootPath || it.startsWith(oldRootPath) }
            .toList()

        val buffer = StructuralBuffer(graphNotation)

        // Re-nest each subtree object by swapping its old root-nesting prefix for the new one (segments past
        // the prefix reference the root + inner containers by name, which are unchanged). Reuses the refactor
        // helper, so references into each object are rewritten as it moves. Re-nesting one object never moves
        // another's path, so each old location is still present when its command runs (same invariant as
        // renameObjectRefactor).
        val nestedObjectRenames = subtreePaths.map { path ->
            val newNesting = ObjectNesting(
                (newObjectNesting.segments +
                    path.nesting.segments.subList(oldNestingSize, path.nesting.segments.size)
                ).toPersistentList())

            nestedRenameObjectRefactor(
                ObjectLocation(documentPath, path),
                newNesting,
                buffer,
                graphDefinitionAttempt)
        }

        // Reposition the now-re-nested subtree as a contiguous block (resolved against the doc minus subtree).
        val shiftedObjectTree = buffer
            .apply(ShiftObjectTreeCommand(
                ObjectLocation(documentPath, newRootPath), newPositionInDocument))
            as ShiftedObjectTreeEvent

        return NotationTransition(
            RelocatedObjectTreeRefactorEvent(nestedObjectRenames, shiftedObjectTree),
            buffer.graphNotation)
    }


    private fun renameNestedObject(
        containerObjectLocation: ObjectLocation,
        newName: ObjectName,
        nestedObjectPath: ObjectPath
    ): ObjectPath {
        val segments = nestedObjectPath.nesting.segments

        val prefix =
            segments.subList(0, containerObjectLocation.objectPath.nesting.segments.size)

        val containingSegment =
            segments[containerObjectLocation.objectPath.nesting.segments.size]

        val renamedSegment = ObjectNestingSegment(
            newName, containingSegment.attributePath)

        // The containing segment sits at index prefix.size; everything after it (the descendant's own deeper
        // nesting) must be preserved verbatim. Starting the suffix at prefix.size + 2 dropped the segment at
        // prefix.size + 1, re-parenting grandchildren up to the renamed container's branch.
        val suffix = segments.subList(prefix.size + 1, segments.size)

        return nestedObjectPath.copy(
            nesting = ObjectNesting((
                prefix + listOf(renamedSegment) + suffix
            ).toPersistentList()))
    }


    private fun nestedRenameObjectRefactor(
        objectLocation: ObjectLocation,
        newObjectNesting: ObjectNesting,
        buffer: StructuralBuffer,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): NestedObjectRename {
        val newObjectPath = objectLocation.objectPath.copy(nesting = newObjectNesting)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
            objectLocation, newObjectLocation, graphDefinitionAttempt)

        val adjustedReferenceEvents = adjustedReferenceCommands
            .map { buffer.apply(it) as UpdatedInAttributeEvent }
            .toList()

        val renamedObject = buffer
            .apply(RenameNestedObjectCommand(objectLocation, newObjectNesting))
            as RenamedNestedObjectEvent

        return NestedObjectRename(
            renamedObject, adjustedReferenceEvents)
    }


    private fun adjustReferenceCommands(
        objectLocation: ObjectLocation,
        newObjectLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): List<UpdateInAttributeCommand> {
        val commands = mutableListOf<UpdateInAttributeCommand>()

        val newFullReference = newObjectLocation.toReference()
        val referenceLocations = locateReferences(objectLocation, graphDefinitionAttempt)
        for (referenceLocation in referenceLocations) {
            val existingReference = existingReference(referenceLocation, graphDefinitionAttempt)
            val newReference = newFullReference.crop(existingReference.hasPath())
            if (existingReference == newReference) {
                continue
            }

            val newReferenceNotation = ScalarAttributeNotation(newReference.asString())
            commands.add(UpdateInAttributeCommand(
                referenceLocation.objectLocation,
                referenceLocation.attributePath,
                newReferenceNotation
            ))
        }

        return commands
    }


    private fun existingReference(
        referenceLocation: AttributeLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): ObjectReference {
        val attributePath = referenceLocation.attributePath

        // NB: top-level 'is:' (incl. list-element 'is[i]:') and meta-attribute inheritance refs
        //  live in the notation, not the definition
        if (isInheritancePath(attributePath) || isMetaInheritancePath(attributePath)) {
            val notation = graphDefinitionAttempt
                    .graphStructure
                    .graphNotation
                    .coalesce
                    .map[referenceLocation.objectLocation]!!
            val scalar = notation.get(attributePath) as ScalarAttributeNotation
            return ObjectReference.parse(scalar.value)
        }

        val existingReferenceDefinition =
            graphDefinitionAttempt
                .objectDefinitions[referenceLocation.objectLocation]
                ?.get(attributePath)
            ?: graphDefinitionAttempt
                .failures[referenceLocation.objectLocation]!!
                .partial!!
                .get(attributePath)
        return (existingReferenceDefinition as ReferenceAttributeDefinition).objectReference!!
    }


    private fun isInheritancePath(path: AttributePath): Boolean {
        if (path == NotationConventions.isAttributePath) {
            return true
        }
        // NB: list-element 'is[i]:' for multiple inheritance
        if (path.attribute != NotationConventions.isAttributeName) {
            return false
        }
        val segments = path.nesting.segments
        return segments.size == 1 && segments.first().asIndex() != null
    }


    private fun isMetaInheritancePath(path: AttributePath): Boolean {
        if (path.attribute != NotationConventions.metaAttributeName) {
            return false
        }
        val segments = path.nesting.segments
        return when (segments.size) {
            1 -> true
            2 -> segments.last() == NotationConventions.isAttributeSegment
            else -> false
        }
    }


    private fun locateReferences(
        objectLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): Set<AttributeLocation> {
        val referenceLocations = mutableSetOf<AttributeLocation>()

        fun locateInObjectDefinition(hostObjectLocation: ObjectLocation, objectDefinition: ObjectDefinition) {
            val attributeReferences =
                    objectDefinition.attributeReferencesIncludingWeak()

            for (attributeReference in attributeReferences) {
                if (!isReferenced(
                        objectLocation,
                        attributeReference.value.objectReference,
                        ObjectReferenceHost.ofLocation(hostObjectLocation),
                        graphDefinitionAttempt)) {
                    continue
                }

                // Skip references that live in a derived/auto-wired attribute with no notation backing — e.g.
                // the synthetic NestedList step lists (and Autowired / ParentChild). They re-compute from object
                // structure after a rename/move, so there is nothing to rewrite; trying would throw in
                // updateInAttribute (which guards on this same null merged attribute).
                if (graphDefinitionAttempt.graphStructure.graphNotation.mergeAttribute(
                        hostObjectLocation, attributeReference.key.attribute) == null) {
                    continue
                }

                val referencingAttribute = AttributeLocation(hostObjectLocation, attributeReference.key)
                referenceLocations.add(referencingAttribute)
            }
        }

        for (e in graphDefinitionAttempt.objectDefinitions.map) {
            locateInObjectDefinition(e.key, e.value)
        }

        for (e in graphDefinitionAttempt.failures.map) {
            val partial = e.value.partial
                ?: continue

            locateInObjectDefinition(e.key, partial)
        }

        referenceLocations.addAll(locateIsReferences(objectLocation, graphDefinitionAttempt))

        return referenceLocations
    }


    private fun locateIsReferences(
        objectLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): Set<AttributeLocation> {
        val referenceLocations = mutableSetOf<AttributeLocation>()
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

        for ((hostObjectLocation, objectNotation) in graphNotation.coalesce.map) {
            val host = ObjectReferenceHost.ofLocation(hostObjectLocation)

            when (val isAttribute = objectNotation.get(NotationConventions.isAttributePath)) {
                is ScalarAttributeNotation -> {
                    if (resolvesToTarget(graphNotation, isAttribute.value, host, objectLocation)) {
                        referenceLocations.add(
                                AttributeLocation(hostObjectLocation, NotationConventions.isAttributePath))
                    }
                }

                is ListAttributeNotation -> {
                    // NB: multiple inheritance — each list element is a parent reference
                    isAttribute.values.forEachIndexed { index, element ->
                        val scalar = element as? ScalarAttributeNotation
                            ?: return@forEachIndexed
                        if (resolvesToTarget(graphNotation, scalar.value, host, objectLocation)) {
                            referenceLocations.add(AttributeLocation(
                                    hostObjectLocation,
                                    NotationConventions.isAttributePath.nest(AttributeSegment.ofIndex(index))))
                        }
                    }
                }

                else -> {}
            }

            // NB: meta.<attr>: OldName  (scalar)  and  meta.<attr>.is: OldName  (map) are both inheritance refs
            val metaAttribute = objectNotation.get(NotationConventions.metaAttributePath) as? MapAttributeNotation
                ?: continue

            for ((metaSegment, metaValue) in metaAttribute.map) {
                val metaAttributePath = NotationConventions.metaAttributePath.nest(metaSegment)

                when (metaValue) {
                    is ScalarAttributeNotation -> {
                        if (resolvesToTarget(graphNotation, metaValue.value, host, objectLocation)) {
                            referenceLocations.add(AttributeLocation(hostObjectLocation, metaAttributePath))
                        }
                    }

                    is MapAttributeNotation -> {
                        val nestedIs = metaValue.map[NotationConventions.isAttributeSegment]
                                as? ScalarAttributeNotation
                            ?: continue
                        if (resolvesToTarget(graphNotation, nestedIs.value, host, objectLocation)) {
                            referenceLocations.add(AttributeLocation(
                                    hostObjectLocation,
                                    metaAttributePath.nest(NotationConventions.isAttributeSegment)))
                        }
                    }

                    else -> {}
                }
            }
        }

        return referenceLocations
    }


    private fun resolvesToTarget(
        graphNotation: GraphNotation,
        value: String,
        host: ObjectReferenceHost,
        target: ObjectLocation
    ): Boolean {
        val reference = ObjectReference.parse(value)
        return graphNotation.coalesce.locateOptional(reference, host) == target
    }


    private fun isReferenced(
        targetLocation: ObjectLocation,
        reference: ObjectReference,
        host: ObjectReferenceHost,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): Boolean {
        val referencedLocation = graphDefinitionAttempt
            .objectDefinitions
            .locateOptional(reference, host)

        if (referencedLocation == targetLocation) {
            return true
        }

        val partialReferencedLocation = graphDefinitionAttempt
            .failures
            .locateOptional(reference, host)

        return partialReferencedLocation == targetLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Relocate a single document to a new path (rename = same nesting / new name; move = same name / new
    // nesting). Copies old→new, deletes old, and rewrites references into the document's root objects.
    private fun relocateDocumentRefactor(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        documentPath: DocumentPath,
        newDocumentPath: DocumentPath
    ): NotationTransition {
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
        val documentNotation = graphNotation.documents.map[documentPath]
        require(documentNotation != null) {
            "documentPath missing: $documentPath - ${graphNotation.documents.map.keys}"
        }
        require(newDocumentPath !in graphNotation.documents.map) {
            "Destination already exists: $newDocumentPath"
        }
        val buffer = StructuralBuffer(graphNotation)

        val createdWithNewName = buffer
            .apply(CopyDocumentCommand(
                documentPath,
                newDocumentPath
            ))
            as CopiedDocumentEvent

        val removedUnderOldName = buffer
            .apply(DeleteDocumentCommand(documentPath))
            as DeletedDocumentEvent

        val adjustedReferenceEvents = adjustReferencesForRenamedDocument(
                documentPath, newDocumentPath, documentNotation, graphDefinitionAttempt, buffer)

        return NotationTransition(
            RenamedDocumentRefactorEvent(
                createdWithNewName,
                removedUnderOldName,
                adjustedReferenceEvents
            ),
            buffer.graphNotation)
    }


    private fun adjustReferencesForRenamedDocument(
        documentPath: DocumentPath,
        newDocumentPath: DocumentPath,
        documentNotation: DocumentNotation,
        graphDefinitionAttempt: GraphDefinitionAttempt,
        buffer: StructuralBuffer
    ): List<UpdatedInAttributeEvent> {
        // NB: only top-level (root) objects cross-document reference are currently supported
        val rootObjectPaths = documentNotation
            .objects
            .notations
            .map
            .keys
            .filter { it.nesting.isRoot() }

        val allAdjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()

        for (adjustedObjectPath in rootObjectPaths) {
            val rootObjectLocation = ObjectLocation(documentPath, adjustedObjectPath)
            val newObjectLocation = ObjectLocation(newDocumentPath, adjustedObjectPath)

            val adjustedReferenceCommands = adjustReferenceCommands(
                rootObjectLocation, newObjectLocation, graphDefinitionAttempt)

            val adjustedReferenceEvents = adjustedReferenceCommands
                .map { buffer.apply(it) as UpdatedInAttributeEvent }

            allAdjustedReferenceEvents.addAll(adjustedReferenceEvents)
        }

        return allAdjustedReferenceEvents
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Relocate a folder and its whole subtree (rename = same nesting / new name; move = same name / new
    // nesting). Every nested document/folder is re-nested by swapping the old content-nesting prefix for the
    // new one, and references into the moved objects are rewritten — including intra-subtree references
    // between two moved documents (handled by adjusting at the referencing object's FINAL location, below).
    private fun relocateFolderRefactor(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        folderPath: DocumentPath,
        newFolderPath: DocumentPath
    ): NotationTransition {
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

        check(folderPath.folder) { "Not a folder: $folderPath" }
        check(newFolderPath.folder) { "Not a folder: $newFolderPath" }
        require(folderPath in graphNotation.documents.map) {
            "Folder missing: $folderPath - ${graphNotation.documents.map.keys}"
        }
        require(newFolderPath !in graphNotation.documents.map) {
            "Destination already exists: $newFolderPath"
        }

        // "foo" at nesting N holds its contents at N + foo
        val oldContentNesting = folderPath.nesting.plus(DocumentSegment(folderPath.name.value))
        val newContentNesting = newFolderPath.nesting.plus(DocumentSegment(newFolderPath.name.value))

        require(!newContentNesting.startsWith(oldContentNesting)) {
            "Cannot move a folder into itself or a descendant: $folderPath -> $newFolderPath"
        }

        fun reNestPath(path: DocumentPath): DocumentPath {
            if (path == folderPath) {
                return newFolderPath
            }
            return path.copy(nesting = path.nesting.replacePrefix(oldContentNesting, newContentNesting))
        }

        // documents + nested folders strictly under the old content nesting
        val descendants = graphNotation.documents.map.keys
            .filter { it.nesting.startsWith(oldContentNesting) }
        val descendantFolders = descendants.filter { it.folder }
        val descendantDocuments = descendants.filter { !it.folder }

        val buffer = StructuralBuffer(graphNotation)

        // 1. create the new folder and its nested folders (empty folders persist this way too)
        val createdFolder = buffer
            .apply(CreateFolderCommand(newFolderPath)) as CreatedFolderEvent
        val createdSubfolders = descendantFolders.map {
            buffer.apply(CreateFolderCommand(reNestPath(it))) as CreatedFolderEvent
        }

        // 2. copy each descendant document to its new location (body byte-identical at this stage)
        val copiedDocuments = descendantDocuments.map {
            buffer.apply(CopyDocumentCommand(it, reNestPath(it))) as CopiedDocumentEvent
        }

        // 3. rewrite references into the moved objects, AT the referencing object's final location (so an
        //    inside→inside reference lands on the just-made copy, which DirectGraphStore then persists)
        val adjustedReferences = adjustReferencesForRelocatedFolder(
            descendantDocuments, oldContentNesting, folderPath, ::reNestPath, graphDefinitionAttempt, buffer)

        // 4. cascade-delete the old subtree (the folder's own entry + everything under its content nesting)
        val removedFolder = buffer
            .apply(DeleteFolderCommand(folderPath)) as DeletedFolderEvent

        return NotationTransition(
            RenamedFolderRefactorEvent(
                createdFolder, createdSubfolders, copiedDocuments, adjustedReferences, removedFolder),
            buffer.graphNotation)
    }


    private fun adjustReferencesForRelocatedFolder(
        movedDocuments: List<DocumentPath>,
        oldContentNesting: DocumentNesting,
        oldFolderPath: DocumentPath,
        reNestPath: (DocumentPath) -> DocumentPath,
        graphDefinitionAttempt: GraphDefinitionAttempt,
        buffer: StructuralBuffer
    ): List<UpdatedInAttributeEvent> {
        val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

        fun finalReferencingLocation(referencingObjectLocation: ObjectLocation): ObjectLocation {
            val documentPath = referencingObjectLocation.documentPath
            val insideSubtree = documentPath == oldFolderPath ||
                    documentPath.nesting.startsWith(oldContentNesting)
            return if (insideSubtree) {
                referencingObjectLocation.copy(documentPath = reNestPath(documentPath))
            }
            else {
                referencingObjectLocation
            }
        }

        val allAdjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()

        for (movedDocument in movedDocuments) {
            // NB: only top-level (root) objects cross-document reference are currently supported
            val rootObjectPaths = graphNotation.documents.map[movedDocument]!!
                .objects
                .notations
                .map
                .keys
                .filter { it.nesting.isRoot() }

            val newDocumentPath = reNestPath(movedDocument)

            for (rootObjectPath in rootObjectPaths) {
                val oldObjectLocation = ObjectLocation(movedDocument, rootObjectPath)
                val newFullReference = ObjectLocation(newDocumentPath, rootObjectPath).toReference()

                val referenceLocations = locateReferences(oldObjectLocation, graphDefinitionAttempt)
                for (referenceLocation in referenceLocations) {
                    val existingReference = existingReference(referenceLocation, graphDefinitionAttempt)
                    val newReference = newFullReference.crop(existingReference.hasPath())
                    if (existingReference == newReference) {
                        continue
                    }

                    val finalObjectLocation = finalReferencingLocation(referenceLocation.objectLocation)
                    val newReferenceNotation = ScalarAttributeNotation(newReference.asString())

                    val event = buffer.apply(UpdateInAttributeCommand(
                        finalObjectLocation,
                        referenceLocation.attributePath,
                        newReferenceNotation
                    )) as UpdatedInAttributeEvent

                    allAdjustedReferenceEvents.add(event)
                }
            }
        }

        return allAdjustedReferenceEvents
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addResource(
        state: GraphNotation,
        command: AddResourceCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.resourceLocation.documentPath]

        checkNotNull(documentNotation) {
            "Document '${command.resourceLocation.documentPath}' does not exist"
        }
        checkNotNull(documentNotation.resources) {
            "Document '${command.resourceLocation.documentPath}' does not have resources"
        }
        check(command.resourceLocation.resourcePath !in documentNotation.resources.digests) {
            "Resource '${command.resourceLocation}' already exists"
        }

        val contentDigest = command.resourceContent.digest()

        val modifiedDocumentNotation =
            documentNotation.withNewResource(
                command.resourceLocation.resourcePath,
                contentDigest)

        val nextState = state.withModifiedDocument(
            command.resourceLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
            AddedResourceEvent(
                command.resourceLocation,
                contentDigest),
            nextState)
    }


    private fun removeResource(
        state: GraphNotation,
        command: RemoveResourceCommand
    ): NotationTransition {
        val documentNotation = state.documents.map[command.resourceLocation.documentPath]

        checkNotNull(documentNotation) {
            "Document '${command.resourceLocation.documentPath}' does not exist"
        }
        checkNotNull(documentNotation.resources) {
            "Document '${command.resourceLocation.documentPath}' does not have resources"
        }
        check(command.resourceLocation.resourcePath in documentNotation.resources.digests) {
            "Resource '${command.resourceLocation}' does not exists"
        }

        val modifiedDocumentNotation =
            documentNotation.withoutResource(
                command.resourceLocation.resourcePath)

        val nextState = state.withModifiedDocument(
            command.resourceLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
            RemovedResourceEvent(
                command.resourceLocation),
            nextState)
    }
}