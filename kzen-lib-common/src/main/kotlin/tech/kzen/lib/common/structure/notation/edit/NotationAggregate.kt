package tech.kzen.lib.common.structure.notation.edit

import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.AttributeLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.notation.model.*


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

            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
    }


    private fun handle(
            command: SemanticNotationCommand,
            graphDefinition: GraphDefinition
    ): EventAndNotation {
        return when (command) {
            is RenameObjectRefactorCommand ->
                renameRefactor(command.objectLocation, command.newName, graphDefinition)

            is RenameDocumentRefactorCommand ->
                renameDocumentRefactor(command.documentPath, command.newName, graphDefinition)
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

        val document = state.documents.get(command.sourceDocumentPath)!!

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

        val modifiedProjectNotation =
                documentNotation.withNewObject(
                        PositionedObjectPath(command.objectLocation.objectPath, command.indexInDocument),
                        command.body)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedProjectNotation)

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

        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(command.objectLocation.objectPath)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedProjectNotation)

        return EventAndNotation(
                RemovedObjectEvent(command.objectLocation),
                nextState)
    }


    private fun shiftObject(
            command: ShiftObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)!!

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
        val objectNotation = state.coalesce.get(command.objectLocation)!!
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun upsertAttribute(
            command: UpsertAttributeCommand
    ): EventAndNotation {
        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce.get(command.objectLocation)!!

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofName(command.attributeName), command.attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedProjectNotation)

        return EventAndNotation(
                UpsertedAttributeEvent(
                        command.objectLocation, command.attributeName, command.attributeNotation),
                nextState)
    }


    private fun updateInAttribute(
            command: UpdateInAttributeCommand
    ): EventAndNotation {
        val packageNotation = state.documents.values[command.objectLocation.documentPath]!!
        val objectNotation = state.coalesce.get(command.objectLocation)!!

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedDocument(
                command.objectLocation.documentPath, modifiedProjectNotation)

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

        val objectNotation = state.coalesce.get(command.objectLocation)!!

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
        val objectNotation = state.coalesce.get(command.objectLocation)!!

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
        val objectNotation = state.coalesce.get(command.objectLocation)!!

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
        val objectNotation = state.coalesce.get(command.objectLocation)!!

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

        val addendReference = objectLocation.toReference().crop(true, false)
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
        val objectNotation = state.coalesce.get(command.containingObjectLocation)!!

//        val containerPath = command.attributePath.parent()
//        val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

        val attributeNotation = objectNotation.get(command.attributePath)!!
        val objectReference = ObjectReference.parse(attributeNotation.asString()!!)
        val objectLocation = state.coalesce.locate(command.containingObjectLocation, objectReference)

        val builder = NotationAggregate(state)

        val removedInAttribute = builder
                .apply(RemoveInAttributeCommand(
                        command.containingObjectLocation, command.attributePath))
                as RemovedInAttributeEvent

        val removedObject = builder
                .apply(RemoveObjectCommand(
                        objectLocation))
                as RemovedObjectEvent

        return EventAndNotation(
                RemovedObjectInAttributeEvent(removedInAttribute, removedObject),
                builder.state)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renameRefactor(
            objectLocation: ObjectLocation,
            newName: ObjectName,
            graphDefinition: GraphDefinition
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val builder = NotationAggregate(state)

        val renamedObject = builder
                .apply(RenameObjectCommand(objectLocation, newName))
                as RenamedObjectEvent

        val adjustedReferenceCommands = adjustReferenceCommands(
                objectLocation, newName, graphDefinition)

        val adjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()
        adjustedReferenceCommands.forEach {
            adjustedReferenceEvents.add(builder.apply(it) as UpdatedInAttributeEvent)
        }

        return EventAndNotation(
                RenamedObjectRefactorEvent(renamedObject, adjustedReferenceEvents),
                builder.state)
    }


    private fun adjustReferenceCommands(
            objectLocation: ObjectLocation,
            newName: ObjectName,
            graphDefinition: GraphDefinition
    ): List<UpdateInAttributeCommand> {
        val newObjectPath = objectLocation.objectPath.copy(name = newName)
        val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)
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
            for (attributeReference in e.value.attributeReferences()) {

                if (! isReferenced(
                                objectLocation,
                                e.key,
                                attributeReference.value,
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
            host: ObjectLocation,
            reference: ObjectReference,
            graphDefinition: GraphDefinition
    ): Boolean {
        val referencedLocation = graphDefinition
                .objectDefinitions
                .locateOptional(host, reference)

        return referencedLocation == targetLocation
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun renameDocumentRefactor(
            documentPath: DocumentPath,
            newName: DocumentName,
            graphDefinition: GraphDefinition
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
}