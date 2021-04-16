package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.AttributeLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectNestingSegment
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentList


class NotationReducer {
    //-----------------------------------------------------------------------------------------------------------------
    private inner class Buffer(
        var state: GraphNotation
    ) {
        fun apply(
            command: StructuralNotationCommand
        ): NotationEvent {
            val transition = apply(state, command)
            state = transition.graphNotation
            return transition.notationEvent
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun apply(
        state: GraphNotation,
        command: StructuralNotationCommand
    ): NotationTransition {
        return when (command) {
            is CreateDocumentCommand ->
                createDocument(state, command)

            is DeleteDocumentCommand ->
                deleteDocument(state, command)

            is CopyDocumentCommand ->
                copyDocument(state, command)


            is AddObjectCommand ->
                addObject(state, command)

            is RemoveObjectCommand ->
                removeObject(state, command)

            is ShiftObjectCommand ->
                shiftObject(state, command)

            is RenameObjectCommand ->
                renameObject(state, command)

            is RenameNestedObjectCommand ->
                renameNestedObject(state, command)


            is UpsertAttributeCommand ->
                upsertAttribute(state, command)

            is UpdateInAttributeCommand ->
                updateInAttribute(state, command)

            is InsertListItemInAttributeCommand ->
                insertListItemInAttribute(state, command)

            is InsertAllListItemsInAttributeCommand ->
                insertAllListItemsInAttribute(state, command)

            is InsertMapEntryInAttributeCommand ->
                insertMapEntryInAttribute(state, command)

            is RemoveInAttributeCommand ->
                removeInAttribute(state, command)

            is RemoveListItemInAttributeCommand ->
                removeListItemInAttribute(state, command)

            is RemoveAllListItemsInAttributeCommand ->
                removeAllListItemsInAttribute(state, command)


            is ShiftInAttributeCommand ->
                shiftInAttribute(state, command)

            is InsertObjectInListAttributeCommand ->
                insertObjectInListAttribute(state, command)

            is RemoveObjectInAttributeCommand ->
                removeObjectInAttribute(state, command)


            is AddResourceCommand ->
                addResource(state, command)

            is RemoveResourceCommand ->
                removeResource(state, command)


            else ->
                throw UnsupportedOperationException("Unknown command: $command")
        }
    }


    fun apply(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        command: SemanticNotationCommand
    ): NotationTransition {
        val state = graphDefinitionAttempt.graphStructure.graphNotation
        return when (command) {
            is RenameObjectRefactorCommand ->
                renameObjectRefactor(state, command.objectLocation, command.newName, graphDefinitionAttempt)

            is RenameDocumentRefactorCommand ->
                renameDocumentRefactor(state, command.documentPath, command.newName, graphDefinitionAttempt)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun createDocument(
        state: GraphNotation,
        command: CreateDocumentCommand
    ): NotationTransition {
        check(! state.documents.values.containsKey(command.documentPath)) {
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
        check(state.documents.values.containsKey(command.documentPath)) {
            "Does not exist: ${command.documentPath} - ${state.documents.values.keys}"
        }

        val nextState = state.withoutDocument(command.documentPath)

        return NotationTransition(
                DeletedDocumentEvent(command.documentPath),
                nextState)
    }


    private fun copyDocument(
        state: GraphNotation,
        command: CopyDocumentCommand
    ): NotationTransition {
        check(command.sourceDocumentPath in state.documents.values) {
            "Does not exist: ${command.sourceDocumentPath} - ${state.documents.values.keys}"
        }

        val document = state.documents[command.sourceDocumentPath]!!

        val withDocumentNotationCopy = state
                .withNewDocument(command.destinationDocumentPath, document)

        return NotationTransition(
                CopiedDocumentEvent(command.sourceDocumentPath, command.destinationDocumentPath),
                withDocumentNotationCopy)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
        state: GraphNotation,
        command: AddObjectCommand
    ): NotationTransition {
        check(command.objectLocation !in state.coalesce.values) {
            "Object named '${command.objectLocation}' already exists"
        }

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!

        val indexInDocument =
            command.indexInDocument.resolve(documentNotation.objects.notations.values.size)

        val modifiedDocumentNotation =
                documentNotation.withNewObject(
                        PositionedObjectPath(command.objectLocation.objectPath, indexInDocument),
                        command.body)

        val nextState = state.withModifiedDocument(
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
        check(command.objectLocation in state.coalesce.values)

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!

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
        check(command.objectLocation in state.coalesce.values)

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!

        val objectNotation = state.coalesce[command.objectLocation]!!

        val removedFromCurrent = documentNotation.withoutObject(command.objectLocation.objectPath)

        val newPositionInDocument =
            command.newPositionInDocument.resolve(documentNotation.objects.notations.values.size)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, newPositionInDocument),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedToNew)

        return NotationTransition(
                ShiftedObjectEvent(command.objectLocation, newPositionInDocument),
                nextState)
    }


    private fun renameObject(
        state: GraphNotation,
        command: RenameObjectCommand
    ): NotationTransition {
        check(command.objectLocation in state.coalesce.values)

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
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
        check(command.objectLocation in state.coalesce.values)

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]
                ?: throw IllegalArgumentException("Unknown document path: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Unknown object location: ${command.objectLocation}")

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofName(command.attributeName), command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
                UpsertedAttributeEvent(
                        command.objectLocation, command.attributeName, command.attributeNotation),
                nextState)
    }


    private fun updateInAttribute(
        state: GraphNotation,
        command: UpdateInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val mergedAttributeNotation = state.mergeAttribute(
            command.objectLocation, command.attributePath.attribute)

        val objectWithMergedAttribute = objectNotation.upsertAttribute(
            command.attributePath.attribute, mergedAttributeNotation)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
                UpdatedInAttributeEvent(
                        command.objectLocation, command.attributePath, command.attributeNotation),
                nextState)
    }


    private fun insertListItemInAttribute(
        state: GraphNotation,
        command: InsertListItemInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val listInAttribute = state
                .firstAttribute(command.objectLocation, command.containingList) as? ListAttributeNotation
                ?: throw IllegalStateException(
                        "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insert(indexInList, command.item)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

        val objectNotation = state.coalesce[command.objectLocation]
            ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val listInAttribute = state
            .firstAttribute(command.objectLocation, command.containingList) as? ListAttributeNotation
            ?: throw IllegalStateException(
                "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insertAll(indexInList, command.items)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
            command.containingList, listWithInsert)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedListItemInAttributeEvent(
            command.objectLocation, command.containingList, indexInList, listInAttribute)

        return NotationTransition(event, nextState)
    }


    private fun insertMapEntryInAttribute(
        state: GraphNotation,
        command: InsertMapEntryInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containingAttribute =
            objectNotation.get(command.containingMap)
//            state.transitiveAttribute(command.objectLocation, command.containingMap)

        require(containingAttribute == null || containingAttribute is MapAttributeNotation) {
            "Map expected: ${command.containingMap} - $containingAttribute"
        }

        val containingMapExists = containingAttribute != null

        val containingMapSize = (containingAttribute as? MapAttributeNotation)?.values?.size ?: 0
        val indexInMap = command.indexInMap.resolve(containingMapSize)

        val createdAncestors = mutableListOf<AttributePath>()

        val modifiedObjectNotation =
            if (! containingMapExists) {
                require(command.createAncestorsIfAbsent) {
                    "Containing map missing: ${command.containingMap}"
                }

                require(indexInMap.value == 0) {
                    "Index out of bounds in empty map: ${command.indexInMap}"
                }

//                val containingMapKey = command.containingMap.nesting.segments.last()
                val containerNotation = MapAttributeNotation(persistentMapOf(
                    command.mapKey to command.value
                ))

                var missingAncestorChain = containerNotation

                var furthestPresentAncestor: AttributePath? = command.containingMap
                while (objectNotation.get(furthestPresentAncestor!!) == null) {
//                while (state.transitiveAttribute(command.objectLocation, furthestPresentAncestor!!) == null) {
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
                    val keyInPresentAncestor = missingAncestorChain.values.keys.first()
                    val notionUnderPresentAncestor = missingAncestorChain.values[keyInPresentAncestor]!!

                    objectNotation.upsertAttribute(
                        furthestPresentAncestor,
                        presentAncestorNotation.put(keyInPresentAncestor, notionUnderPresentAncestor))
                }
            }
            else {
                val mapInAttribute = containingAttribute as MapAttributeNotation
                val mapWithInsert = mapInAttribute.insert(
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
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
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.attributePath.parent()
        val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

        val attributeNotation = objectNotation.get(command.attributePath)!!

        val builder = Buffer(state)

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
                builder.state)
    }


    private fun insertObjectInListAttribute(
        state: GraphNotation,
        command: InsertObjectInListAttributeCommand
    ): NotationTransition {
        val buffer = Buffer(state)

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
                buffer.state)
    }


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

        val buffer = Buffer(state)

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
                .state
                .documents[containingDocumentPath]!!
                .objects
                .notations
                .values
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
                buffer.state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renameObjectRefactor(
        state: GraphNotation,
        objectLocation: ObjectLocation,
        newName: ObjectName,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): NotationTransition {
        check(objectLocation in state.coalesce.values)

        val buffer = Buffer(state)

        val nestedObjectLocations = graphDefinitionAttempt
            .graphStructure
            .graphNotation
            .documents[objectLocation.documentPath]!!
            .objects
            .notations
            .values
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

        val renamedObject = buffer
                .apply(RenameObjectCommand(objectLocation, newName))
                as RenamedObjectEvent

        val newObjectPath = objectLocation.objectPath.copy(name = newName)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinitionAttempt)

        val adjustedReferenceEvents = adjustedReferenceCommands
                .map { buffer.apply(it) as UpdatedInAttributeEvent }
                .toList()

        return NotationTransition(
                RenamedObjectRefactorEvent(
                        renamedObject,
                        adjustedReferenceEvents,
                        nestedObjects),
                buffer.state)
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

        val suffix = segments.subList(prefix.size + 2, segments.size)

        return nestedObjectPath.copy(
                nesting = ObjectNesting((
                        prefix + listOf(renamedSegment) + suffix
                ).toPersistentList()))
    }


    private fun nestedRenameObjectRefactor(
        objectLocation: ObjectLocation,
        newObjectNesting: ObjectNesting,
        buffer: Buffer,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): NestedObjectRename {
        val renamedObject = buffer
                .apply(RenameNestedObjectCommand(objectLocation, newObjectNesting))
                as RenamedNestedObjectEvent

        val newObjectLocation = renamedObject.newObjectLocation()

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinitionAttempt)

        val adjustedReferenceEvents = adjustedReferenceCommands
                .map { buffer.apply(it) as UpdatedInAttributeEvent }
                .toList()

        return NestedObjectRename(
                renamedObject, adjustedReferenceEvents)
    }


    private fun adjustReferenceCommands(
        objectLocation: ObjectLocation,
        newObjectLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): List<UpdateInAttributeCommand> {
        val newFullReference = newObjectLocation.toReference()

        val commands = mutableListOf<UpdateInAttributeCommand>()

        val referenceLocations = locateReferences(objectLocation, graphDefinitionAttempt)

        for (referenceLocation in referenceLocations) {
            val existingReferenceDefinition =
                    graphDefinitionAttempt
                        .objectDefinitions[referenceLocation.objectLocation]
                        ?.get(referenceLocation.attributePath)
                    ?: graphDefinitionAttempt
                            .failures[referenceLocation.objectLocation]!!
                            .partial!!
                            .get(referenceLocation.attributePath)

            val existingReference =
                    (existingReferenceDefinition as ReferenceAttributeDefinition).objectReference!!

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


    private fun locateReferences(
        objectLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): Set<AttributeLocation> {
        val referenceLocations = mutableSetOf<AttributeLocation>()

        fun locateInObjectDefinition(hostObjectLocation: ObjectLocation, objectDefinition: ObjectDefinition) {
            val attributeReferences =
                    objectDefinition.attributeReferencesIncludingWeak()

            for (attributeReference in attributeReferences) {
                if (! isReferenced(
                                objectLocation,
                                attributeReference.value,
                                ObjectReferenceHost.ofLocation(hostObjectLocation),
                                graphDefinitionAttempt)) {
                    continue
                }

                val referencingAttribute = AttributeLocation(attributeReference.key, hostObjectLocation)
                referenceLocations.add(referencingAttribute)
            }
        }

        for (e in graphDefinitionAttempt.objectDefinitions.values) {
            locateInObjectDefinition(e.key, e.value)
        }

        for (e in graphDefinitionAttempt.failures.values) {
            val partial = e.value.partial
                    ?: continue

            locateInObjectDefinition(e.key, partial)
        }

        return referenceLocations
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
    private fun renameDocumentRefactor(
        state: GraphNotation,
        documentPath: DocumentPath,
        newName: DocumentName,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): NotationTransition {
        val documentNotation = state.documents.values[documentPath]
        require(documentNotation != null) {
            "documentPath missing: $documentPath - ${state.documents.values.keys}"
        }
        val buffer = Buffer(state)

        val newDocumentPath = documentPath.withName(newName)

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
                buffer.state)
    }


    private fun adjustReferencesForRenamedDocument(
        documentPath: DocumentPath,
        newDocumentPath: DocumentPath,
        documentNotation: DocumentNotation,
        graphDefinitionAttempt: GraphDefinitionAttempt,
        buffer: Buffer
    ): List<UpdatedInAttributeEvent> {
        // NB: only top-level (root) objects cross-document reference are currently supported
        val rootObjectPaths = documentNotation
                .objects
                .notations
                .values
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
    private fun addResource(
        state: GraphNotation,
        command: AddResourceCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.resourceLocation.documentPath]

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
        val documentNotation = state.documents.values[command.resourceLocation.documentPath]

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