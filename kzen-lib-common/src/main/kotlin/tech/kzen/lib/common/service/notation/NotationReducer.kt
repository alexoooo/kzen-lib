package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
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
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.toPersistentList


class NotationReducer {
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        fun buffer()
//    }

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

            is InsertMapEntryInAttributeCommand ->
                insertMapEntryInAttribute(state, command)

            is RemoveInAttributeCommand ->
                removeInAttribute(state, command)


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
            graphDefinition: GraphDefinition,
            command: SemanticNotationCommand
    ): NotationTransition {
        val state = graphDefinition.graphStructure.graphNotation
        return when (command) {
            is RenameObjectRefactorCommand ->
                renameObjectRefactor(state, command.objectLocation, command.newName, graphDefinition)

            is RenameDocumentRefactorCommand ->
                renameDocumentRefactor(state, command.documentPath, command.newName/*, graphDefinition*/)
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

        val nextState = state.withNewDocument(
                command.documentPath, command.documentNotation)

        val event = CreatedDocumentEvent(
                command.documentPath, command.documentNotation)

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

        val nextState = state
                .withNewDocument(command.destinationDocumentPath, document)

        return NotationTransition(
                CopiedDocumentEvent(command.sourceDocumentPath, command.destinationDocumentPath),
                nextState)
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

        val modifiedDocumentNotation =
                documentNotation.withNewObject(
                        PositionedObjectPath(command.objectLocation.objectPath, command.indexInDocument),
                        command.body)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return NotationTransition(
                AddedObjectEvent(
                        command.objectLocation,
                        command.indexInDocument,
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

        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!

        val objectNotation = state.coalesce[command.objectLocation]!!

        val removedFromCurrent = packageNotation.withoutObject(command.objectLocation.objectPath)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, command.newPositionInDocument),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedToNew)

        return NotationTransition(
                ShiftedObjectEvent(command.objectLocation, command.newPositionInDocument),
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
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

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

        val modifiedObjectNotation = objectNotation.upsertAttribute(
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

        val objectNotation = state.coalesce[command.objectLocation]!!

        val listInAttribute = state
                .transitiveAttribute(command.objectLocation, command.containingList) as? ListAttributeNotation
                ?: throw IllegalStateException(
                        "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val listWithInsert = listInAttribute.insert(command.indexInList, command.item)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.containingList, listWithInsert)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedListItemInAttributeEvent(
                command.objectLocation, command.containingList, command.indexInList, listInAttribute)

        return NotationTransition(event, nextState)
    }


    private fun insertMapEntryInAttribute(
            state: GraphNotation,
            command: InsertMapEntryInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val mapInAttribute = objectNotation.get(command.containingMap) as MapAttributeNotation
        val mapWithInsert = mapInAttribute.insert(command.value, command.mapKey, command.indexInMap)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.containingMap, mapWithInsert)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = InsertedMapEntryInAttributeEvent(
                command.objectLocation,
                command.containingMap,
                command.indexInMap,
                command.mapKey,
                command.value)

        return NotationTransition(event, nextState)
    }


    private fun removeInAttribute(
            state: GraphNotation,
            command: RemoveInAttributeCommand
    ): NotationTransition {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.attributePath.parent()
        val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

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

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                containerPath, containerWithoutElement)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        val event = RemovedInAttributeEvent(
                command.objectLocation, command.attributePath)

        return NotationTransition(event, nextState)
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
                .apply(RemoveInAttributeCommand(command.objectLocation, command.attributePath))
                as RemovedInAttributeEvent

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
                        attributeNotation)
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

        val objectPath = command.containingObjectLocation.objectPath.nest(
                command.containingList, command.objectName)

        val objectLocation = ObjectLocation(command.containingObjectLocation.documentPath, objectPath)

        val objectAdded = buffer
                .apply(AddObjectCommand(
                        objectLocation,
                        command.positionInDocument,
                        command.body))
                as AddedObjectEvent

        val addendReference = objectLocation.toReference()
                .crop(retainNesting = true, retainPath = false)

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
                        command.containingObjectLocation, command.attributePath))
                as RemovedInAttributeEvent

        val removedObject = buffer
                .apply(RemoveObjectCommand(
                        objectLocation))
                as RemovedObjectEvent

        val containingDocumentPath = command.containingObjectLocation.documentPath

        val nestedObjectLocations = buffer
                .state
                .documents[containingDocumentPath]!!
                .objects
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
            graphDefinition: GraphDefinition
    ): NotationTransition {
        check(objectLocation in state.coalesce.values)

        val buffer = Buffer(state)

        val nestedObjectLocations = graphDefinition
                .objectDefinitions
                .values
                .keys
                .filter { it.startsWith(objectLocation) }
                .map { it to renameNestedObject(objectLocation, newName, it) }
                .toMap()

        val nestedObjects = nestedObjectLocations.map {
            nestedRenameObjectRefactor(
                    it.key,
                    it.value.objectPath.nesting,
                    buffer,
                    graphDefinition
            )
        }

        val renamedObject = buffer
                .apply(RenameObjectCommand(objectLocation, newName))
                as RenamedObjectEvent

        val newObjectPath = objectLocation.objectPath.copy(name = newName)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinition)

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
            nestedObjectLocation: ObjectLocation
    ): ObjectLocation {
        val segments = nestedObjectLocation.objectPath.nesting.segments

        val prefix = segments.subList(0, containerObjectLocation.objectPath.nesting.segments.size)

        val containingSegment = segments[containerObjectLocation.objectPath.nesting.segments.size]
        val renamedSegment = ObjectNestingSegment(
                newName, containingSegment.attributePath)

        val suffix = segments.subList(prefix.size + 2, segments.size)

        return nestedObjectLocation.copy(
                objectPath = nestedObjectLocation.objectPath.copy(
                        nesting = ObjectNesting((
                                prefix + listOf(renamedSegment) + suffix
                        ).toPersistentList())
                ))
    }


    private fun nestedRenameObjectRefactor(
            objectLocation: ObjectLocation,
            newObjectNesting: ObjectNesting,
            buffer: NotationReducer.Buffer,
            graphDefinition: GraphDefinition
    ): NestedObjectRename {
        val renamedObject = buffer
                .apply(RenameNestedObjectCommand(objectLocation, newObjectNesting))
                as RenamedNestedObjectEvent

        val newObjectLocation = renamedObject.newLocation()

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinition)

        val adjustedReferenceEvents = adjustedReferenceCommands
                .map { buffer.apply(it) as UpdatedInAttributeEvent }
                .toList()

        return NestedObjectRename(
                renamedObject, adjustedReferenceEvents)
    }


    private fun adjustReferenceCommands(
            objectLocation: ObjectLocation,
            newObjectLocation: ObjectLocation,
            graphDefinition: GraphDefinition
    ): List<UpdateInAttributeCommand> {
        val newFullReference = newObjectLocation.toReference()

        val commands = mutableListOf<UpdateInAttributeCommand>()

        val referenceLocations = locateReferences(objectLocation, graphDefinition)

        for (referenceLocation in referenceLocations) {
            val existingReferenceDefinition = graphDefinition.get(referenceLocation)
            val existingReference = (existingReferenceDefinition as ReferenceAttributeDefinition).objectReference!!

            val newReference = newFullReference.crop(
                    existingReference.hasNesting(), existingReference.hasPath())
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
            graphDefinition: GraphDefinition
    ): Set<AttributeLocation> {
        val referenceLocations = mutableSetOf<AttributeLocation>()

        for (e in graphDefinition.objectDefinitions.values) {
            for (attributeReference in e.value.attributeReferencesIncludingWeak()) {

                if (! isReferenced(
                                objectLocation,
                                attributeReference.value,
                                ObjectReferenceHost.ofLocation(e.key),
                                graphDefinition)) {
                    continue
                }

                val referencingAttribute = AttributeLocation(attributeReference.key, e.key)
                referenceLocations.add(referencingAttribute)
            }
        }

        return referenceLocations
    }


    private fun isReferenced(
            targetLocation: ObjectLocation,
            reference: ObjectReference,
            host: ObjectReferenceHost,
            graphDefinition: GraphDefinition
    ): Boolean {
        val referencedLocation = graphDefinition
                .objectDefinitions
                .locateOptional(reference, host)

        return referencedLocation == targetLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renameDocumentRefactor(
            state: GraphNotation,
            documentPath: DocumentPath,
            newName: DocumentName/*,
            graphDefinition: GraphDefinition*/
    ): NotationTransition {
        require(documentPath in state.documents.values) {
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

//        val adjustedReferenceCommands = adjustReferenceCommands(
//                objectLocation, newName, graphDefinition)
//
//        val adjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()
//        adjustedReferenceCommands.forEach {
//            adjustedReferenceEvents.add(builder.apply(it) as UpdatedInAttributeEvent)
//        }
//
        return NotationTransition(
                RenamedDocumentRefactorEvent(
                        createdWithNewName,
                        removedUnderOldName
                ),
                buffer.state)
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
        check(command.resourceLocation.resourcePath !in documentNotation.resources.values) {
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
        check(command.resourceLocation.resourcePath in documentNotation.resources.values) {
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