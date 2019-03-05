package tech.kzen.lib.common.structure.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
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
            is CreateBundleCommand ->
                createBundle(command)

            is DeleteBundleCommand ->
                deleteBundle(command)


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


            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
    }


    private fun handle(
            command: SemanticNotationCommand,
            graphDefinition: GraphDefinition
    ): EventAndNotation {
        return when (command) {
            is RenameRefactorCommand ->
                renameRefactor(command.objectLocation, command.newName, graphDefinition)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun createBundle(
            command: CreateBundleCommand
    ): EventAndNotation {
        check(! state.bundles.values.containsKey(command.bundlePath)) {
            "Already exists: ${command.bundlePath}"
        }

        val nextState = state.withNewBundle(
                command.bundlePath, BundleNotation.empty)

        return EventAndNotation(
                CreatedBundleEvent(command.bundlePath),
                nextState)
    }


    private fun deleteBundle(
            command: DeleteBundleCommand
    ): EventAndNotation {
        check(state.bundles.values.containsKey(command.bundlePath)) {
            "Does not exist: ${command.bundlePath} - ${state.bundles.values.keys}"
        }

        val nextState = state.withoutBundle(command.bundlePath)

        return EventAndNotation(
                DeletedBundleEvent(command.bundlePath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            command: AddObjectCommand
    ): EventAndNotation {
        check(command.objectLocation !in state.coalesce.values) {
            "Object named '${command.objectLocation}' already exists"
        }

        val bundleNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                bundleNotation.withNewObject(
                        PositionedObjectPath(command.objectLocation.objectPath, command.indexInBundle),
                        command.body)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                AddedObjectEvent(
                        command.objectLocation,
                        command.indexInBundle,
                        command.body),
                nextState)
    }


    private fun removeObject(
            command: RemoveObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val packageNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(command.objectLocation.objectPath)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                RemovedObjectEvent(command.objectLocation),
                nextState)
    }


    private fun shiftObject(
            command: ShiftObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val packageNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

        val removedFromCurrent = packageNotation.withoutObject(command.objectLocation.objectPath)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, command.newPositionInBundle),
                objectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, addedToNew)

        return EventAndNotation(
                ShiftedObjectEvent(command.objectLocation, command.newPositionInBundle),
                nextState)
    }


    private fun renameObject(
            command: RenameObjectCommand
    ): EventAndNotation {
        check(command.objectLocation in state.coalesce.values)

        val packageNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)
        val objectIndex = packageNotation.indexOf(command.objectLocation.objectPath)

        val removedCurrentName =
                packageNotation.withoutObject(command.objectLocation.objectPath)

        val newObjectPath = command.objectLocation.objectPath.copy(name = command.newName)

        val addedWithNewName = removedCurrentName.withNewObject(
                PositionedObjectPath(newObjectPath, objectIndex),
                objectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, addedWithNewName)

        return EventAndNotation(
                RenamedObjectEvent(command.objectLocation, command.newName),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun upsertAttribute(
            command: UpsertAttributeCommand
    ): EventAndNotation {
        val packageNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofAttribute(command.attributeName), command.attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                UpsertedAttributeEvent(
                        command.objectLocation, command.attributeName, command.attributeNotation),
                nextState)
    }


    private fun updateInAttribute(
            command: UpdateInAttributeCommand
    ): EventAndNotation {
        val packageNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                UpdatedInAttributeEvent(
                        command.objectLocation, command.attributePath, command.attributeNotation),
                nextState)
    }


    private fun insertListItemInAttribute(
            command: InsertListItemInAttributeCommand
    ): EventAndNotation {
        val bundleNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

        val listInAttribute = objectNotation.get(command.containingList) as ListAttributeNotation
        val listWithInsert = listInAttribute.insert(command.item, command.indexInList)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.containingList, listWithInsert)

        val modifiedBundleNotation = bundleNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedBundleNotation)

        val event = InsertedListItemInAttributeEvent(
                command.objectLocation, command.containingList, command.indexInList, listInAttribute)

        return EventAndNotation(event, nextState)
    }


    private fun insertMapEntryInAttribute(
            command: InsertMapEntryInAttributeCommand
    ): EventAndNotation {
        val bundleNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

        val mapInAttribute = objectNotation.get(command.containingMap) as MapAttributeNotation
        val mapWithInsert = mapInAttribute.insert(command.value, command.mapKey, command.indexInMap)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                command.containingMap, mapWithInsert)

        val modifiedBundleNotation = bundleNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedBundleNotation)

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
        val bundleNotation = state.bundles.values[command.objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(command.objectLocation)

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

        val modifiedBundleNotation = bundleNotation.withModifiedObject(
                command.objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                command.objectLocation.bundlePath, modifiedBundleNotation)

        val event = RemovedInAttributeEvent(
                command.objectLocation, command.attributePath)

        return EventAndNotation(event, nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun shiftInAttribute(
            command: ShiftInAttributeCommand
    ): EventAndNotation {
        val objectNotation = state.coalesce.get(command.objectLocation)

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

        val objectLocation = ObjectLocation(command.containingObjectLocation.bundlePath, objectPath)

        val objectAdded = builder
                .apply(AddObjectCommand(
                        objectLocation,
                        command.positionInBundle,
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
                InsertedObjectListItemInAttributeEvent(objectAdded, insertedInAttribute),
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
                RenameRefactoredEvent(renamedObject, adjustedReferenceEvents),
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
}