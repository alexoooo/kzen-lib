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


class NotationAggregate(
        var state: GraphNotation
) {
    //-----------------------------------------------------------------------------------------------------------------
    private data class EventAndNotation(
            val event: NotationEvent,
            val notation: GraphNotation)


    //-----------------------------------------------------------------------------------------------------------------
    fun apply(command: StructuralNotationCommand): NotationEvent {
        val eventAndNotation = handle(command)
        state = eventAndNotation.notation
        return eventAndNotation.event
    }


    fun apply(command: SemanticNotationCommand, graphDefinition: GraphDefinition): NotationEvent {
        val eventAndNotation = handle(command, graphDefinition)
        state = eventAndNotation.notation
        return eventAndNotation.event
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handle(command: StructuralNotationCommand): EventAndNotation {
        return when (command) {
            is CreateDocumentCommand ->
                createDocument(command)

            is DeleteDocumentCommand ->
                deleteDocument(command)

            is CopyDocumentCommand ->
                copyDocument(command)


            is AddObjectCommand ->
                addObject(command)

            is RemoveObjectCommand ->
                removeObject(command)

            is ShiftObjectCommand ->
                shiftObject(command)

            is RenameObjectCommand ->
                renameObject(command)

            is RenameNestedObjectCommand ->
                renameNestedObject(command)


            is UpsertAttributeCommand ->
                upsertAttribute(command)

            is UpdateInAttributeCommand ->
                updateInAttribute(command)

            is InsertListItemInAttributeCommand ->
                insertListItemInAttribute(command)

            is InsertMapEntryInAttributeCommand ->
                insertMapEntryInAttribute(command)

            is RemoveInAttributeCommand ->
                removeInAttribute(command)


            is ShiftInAttributeCommand ->
                shiftInAttribute(command)

            is InsertObjectInListAttributeCommand ->
                insertObjectInListAttribute(command)

            is RemoveObjectInAttributeCommand ->
                removeObjectInAttribute(command)


            is AddResourceCommand ->
                addResource(command)

            is RemoveResourceCommand ->
                removeResource(command)


            else ->
                throw UnsupportedOperationException("Unknown command: $command")
        }
    }


    private fun handle(
            command: SemanticNotationCommand,
            graphDefinition: GraphDefinition
    ): EventAndNotation {
        return when (command) {
            is RenameObjectRefactorCommand ->
                renameObjectRefactor(command.objectLocation, command.newName, graphDefinition)

            is RenameDocumentRefactorCommand ->
                renameDocumentRefactor(command.documentPath, command.newName/*, graphDefinition*/)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun createDocument(
            command: CreateDocumentCommand
    ): EventAndNotation {
        check(! state.documents.values.containsKey(command.documentPath)) {
            "Already exists: ${command.documentPath}"
        }

        val nextState = state.withNewDocument(
                command.documentPath, command.documentNotation)

        val event = CreatedDocumentEvent(
                command.documentPath, command.documentNotation)

        return EventAndNotation(event, nextState)
    }


    private fun deleteDocument(
            command: DeleteDocumentCommand
    ): EventAndNotation {
        check(state.documents.values.containsKey(command.documentPath)) {
            "Does not exist: ${command.documentPath} - ${state.documents.values.keys}"
        }

        val nextState = state.withoutDocument(command.documentPath)

        return EventAndNotation(
                DeletedDocumentEvent(command.documentPath),
                nextState)
    }


    private fun copyDocument(
            command: CopyDocumentCommand
    ): EventAndNotation {
        check(command.sourceDocumentPath in state.documents.values) {
            "Does not exist: ${command.sourceDocumentPath} - ${state.documents.values.keys}"
        }

        val document = state.documents[command.sourceDocumentPath]!!

        val nextState = state
                .withNewDocument(command.destinationDocumentPath, document)

        return EventAndNotation(
                CopiedDocumentEvent(command.sourceDocumentPath, command.destinationDocumentPath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            command: AddObjectCommand
    ): EventAndNotation {
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

        return EventAndNotation(
                AddedObjectEvent(
                        command.objectLocation,
                        command.indexInDocument,
                        command.body),
                nextState)
    }


    private fun removeObject(
            command: RemoveObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!

        val modifiedDocumentNotation =
                documentNotation.withoutObject(command.objectLocation.objectPath)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return EventAndNotation(
                RemovedObjectEvent(command.objectLocation),
                nextState)
    }


    private fun shiftObject(
            command: ShiftObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!

        val objectNotation = state.coalesce[command.objectLocation]!!

        val removedFromCurrent = packageNotation.withoutObject(command.objectLocation.objectPath)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, command.newPositionInDocument),
                objectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, addedToNew)

        return EventAndNotation(
                ShiftedObjectEvent(command.objectLocation, command.newPositionInDocument),
                nextState)
    }


    private fun renameObject(
            command: RenameObjectCommand
    ): EventAndNotation {
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

        return EventAndNotation(
                RenamedObjectEvent(command.objectLocation, command.newName),
                nextState)
    }


    private fun renameNestedObject(
            command: RenameNestedObjectCommand
    ): EventAndNotation {
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

        return EventAndNotation(
                RenamedNestedObjectEvent(
                        command.objectLocation, command.newObjectNesting),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun upsertAttribute(
            command: UpsertAttributeCommand
    ): EventAndNotation {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]!!

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofName(command.attributeName), command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return EventAndNotation(
                UpsertedAttributeEvent(
                        command.objectLocation, command.attributeName, command.attributeNotation),
                nextState)
    }


    private fun updateInAttribute(
            command: UpdateInAttributeCommand
    ): EventAndNotation {
        val documentNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce[command.objectLocation]
                ?: throw IllegalArgumentException("Not found: ${command.objectLocation}")

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val modifiedDocumentNotation = documentNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedDocumentNotation)

        return EventAndNotation(
                UpdatedInAttributeEvent(
                        command.objectLocation, command.attributePath, command.attributeNotation),
                nextState)
    }


    private fun insertListItemInAttribute(
            command: InsertListItemInAttributeCommand
    ): EventAndNotation {
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

        return EventAndNotation(event, nextState)
    }


    private fun insertMapEntryInAttribute(
            command: InsertMapEntryInAttributeCommand
    ): EventAndNotation {
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

        return EventAndNotation(event, nextState)
    }


    private fun removeInAttribute(
            command: RemoveInAttributeCommand
    ): EventAndNotation {
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

        return EventAndNotation(event, nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun shiftInAttribute(
            command: ShiftInAttributeCommand
    ): EventAndNotation {
        val objectNotation = state.coalesce[command.objectLocation]!!

        val containerPath = command.attributePath.parent()
        val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

        val attributeNotation = objectNotation.get(command.attributePath)!!

        val builder = NotationAggregate(state)

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

        return EventAndNotation(
                ShiftedInAttributeEvent(removedInAttribute, reinsertedInAttribute),
                builder.state)
    }


    private fun insertObjectInListAttribute(
            command: InsertObjectInListAttributeCommand
    ): EventAndNotation {
        val builder = NotationAggregate(state)

        val objectPath = command.containingObjectLocation.objectPath.nest(
                command.containingList, command.objectName)

        val objectLocation = ObjectLocation(command.containingObjectLocation.documentPath, objectPath)

        val objectAdded = builder
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

        val insertedInAttribute = builder
                .apply(insertInAttributeCommand)
                as InsertedListItemInAttributeEvent

        return EventAndNotation(
                InsertedObjectInListAttributeEvent(objectAdded, insertedInAttribute),
                builder.state)
    }


    private fun removeObjectInAttribute(
            command: RemoveObjectInAttributeCommand
    ): EventAndNotation {
        val objectNotation = state.coalesce[command.containingObjectLocation]
                ?: throw IllegalArgumentException("Containing object not found: ${command.containingObjectLocation}")

        val attributeNotation = objectNotation.get(command.attributePath)!!
        val objectReference = ObjectReference.parse(attributeNotation.asString()!!)
        val objectReferenceHost = ObjectReferenceHost.ofLocation(command.containingObjectLocation)
        val objectLocation = state.coalesce.locate(objectReference, objectReferenceHost)

        val builder = NotationAggregate(state)

        val removedInAttribute = builder
                .apply(RemoveInAttributeCommand(
                        command.containingObjectLocation, command.attributePath))
                as RemovedInAttributeEvent

        val removedObject = builder
                .apply(RemoveObjectCommand(
                        objectLocation))
                as RemovedObjectEvent

        val containingDocumentPath = command.containingObjectLocation.documentPath

        val nestedObjectLocations = builder
                .state
                .documents[containingDocumentPath]!!
                .objects
                .values
                .keys
                .filter { it.startsWith(objectLocation.objectPath) }
                .toList()

        val removedNestedObjects = nestedObjectLocations
                .map {
                    builder.apply(RemoveObjectCommand(
                            ObjectLocation(containingDocumentPath, it)
                    )) as RemovedObjectEvent
                }

        return EventAndNotation(
                RemovedObjectInAttributeEvent(
                        removedInAttribute, removedObject, removedNestedObjects),
                builder.state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renameObjectRefactor(
            objectLocation: ObjectLocation,
            newName: ObjectName,
            graphDefinition: GraphDefinition
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val builder = NotationAggregate(state)

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
                    builder,
                    graphDefinition
            )
        }

        val renamedObject = builder
                .apply(RenameObjectCommand(objectLocation, newName))
                as RenamedObjectEvent

        val newObjectPath = objectLocation.objectPath.copy(name = newName)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinition)

        val adjustedReferenceEvents = adjustedReferenceCommands
                .map { builder.apply(it) as UpdatedInAttributeEvent }
                .toList()

        return EventAndNotation(
                RenamedObjectRefactorEvent(
                        renamedObject,
                        adjustedReferenceEvents,
                        nestedObjects),
                builder.state)
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
            builder: NotationAggregate,
            graphDefinition: GraphDefinition
    ): NestedObjectRename {
        val renamedObject = builder
                .apply(RenameNestedObjectCommand(objectLocation, newObjectNesting))
                as RenamedNestedObjectEvent

        val newObjectLocation = renamedObject.newLocation()

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newObjectLocation, graphDefinition)

        val adjustedReferenceEvents = adjustedReferenceCommands
                .map { builder.apply(it) as UpdatedInAttributeEvent }
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
            documentPath: DocumentPath,
            newName: DocumentName/*,
            graphDefinition: GraphDefinition*/
    ): EventAndNotation {
        check(documentPath in state.documents.values)
        val builder = NotationAggregate(state)

        val newDocumentPath = documentPath.withName(newName)

        val createdWithNewName = builder
                .apply(CopyDocumentCommand(
                        documentPath,
                        newDocumentPath
                ))
                as CopiedDocumentEvent

        val removedUnderOldName = builder
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
        return EventAndNotation(
                RenamedDocumentRefactorEvent(
                        createdWithNewName,
                        removedUnderOldName
                ),
                builder.state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addResource(
            command: AddResourceCommand
    ): EventAndNotation {
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

        return EventAndNotation(
                AddedResourceEvent(
                        command.resourceLocation,
                        contentDigest),
                nextState)
    }


    private fun removeResource(
            command: RemoveResourceCommand
    ): EventAndNotation {
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

        return EventAndNotation(
                RemovedResourceEvent(
                        command.resourceLocation),
                nextState)
    }
}