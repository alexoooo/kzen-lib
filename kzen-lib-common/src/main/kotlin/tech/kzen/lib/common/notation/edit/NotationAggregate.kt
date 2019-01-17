package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.notation.model.*


class NotationAggregate(
        var state: NotationTree
) {
    //-----------------------------------------------------------------------------------------------------------------
    private data class EventAndNotation(
            val event: NotationEvent,
            val notation: NotationTree)


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
                createPackage(command.bundlePath)

            is DeletePackageCommand ->
                deletePackage(command.filePath)


            is AddObjectCommand ->
                addObject(command.location.objectLocation, command.location.positionIndex, command.body)

            is RemoveObjectCommand ->
                removeObject(command.location)

            is ShiftObjectCommand ->
                shiftObject(command.location, command.newPositionInBundle)

            is RenameObjectCommand ->
                renameObject(command.location, command.newName)


            is UpsertAttributeCommand ->
                upsertAttribute(command.objectLocation, command.attributeName, command.attributeNotation)

            is UpdateInAttributeCommand ->
                updateInAttribute(command.objectLocation, command.attributeNesting, command.attributeNotation)

            is InsertListItemInAttributeCommand ->
                insertListItemInAttribute(command.objectLocation, command.containingList, command.item)

            is InsertObjectInListAttributeCommand ->
                insertObjectInListAttribute(
                        command.containingObjectLocation,
                        command.containingListPosition,
                        command.objectName,
                        command.positionInBundle,
                        command.body)


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
    private fun createPackage(
            projectPath: BundlePath
    ): EventAndNotation {
        check(! state.bundleNotations.values.containsKey(projectPath)) {"Already exists: $projectPath"}

        val nextState = state.withNewBundle(
                projectPath, BundleNotation.empty)

        return EventAndNotation(
                CreatedBundleEvent(projectPath),
                nextState)
    }


    private fun deletePackage(
            projectPath: BundlePath
    ): EventAndNotation {
        check(state.bundleNotations.values.containsKey(projectPath)) {"Does not exist: $projectPath"}

        val nextState = state.withoutBundle(projectPath)

        return EventAndNotation(
                DeletedBundleEvent(projectPath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            objectLocation: ObjectLocation,
            indexInBundle: PositionIndex,
            body: ObjectNotation
    ): EventAndNotation {
        check(objectLocation !in state.coalesce.values) {"Object named '$objectLocation' already exists"}

        val bundleNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                bundleNotation.withNewObject(
                        PositionedObjectPath(objectLocation.objectPath, indexInBundle),
                        body)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                AddedObjectEvent(objectLocation, body),
                nextState)
    }


    private fun removeObject(
            objectLocation: ObjectLocation
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val packageNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(objectLocation.objectPath)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                RemovedObjectEvent(objectLocation),
                nextState)
    }


    private fun shiftObject(
            objectLocation: ObjectLocation,
            newPositionInBundle: PositionIndex
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val packageNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val removedFromCurrent = packageNotation.withoutObject(objectLocation.objectPath)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(objectLocation.objectPath, newPositionInBundle),
                objectNotation)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, addedToNew)

        return EventAndNotation(
                ShiftedObjectEvent(objectLocation, newPositionInBundle),
                nextState)
    }


    private fun renameObject(
            objectLocation: ObjectLocation,
            newName: ObjectName
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

//        val projectPath = state.findPackage(objectName)

        val packageNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)
        val objectIndex = packageNotation.indexOf(objectLocation.objectPath)

        val removedCurrentName =
                packageNotation.withoutObject(objectLocation.objectPath)

        val newObjectPath = objectLocation.objectPath.copy(name = newName)

        val addedWithNewName = removedCurrentName.withNewObject(
                PositionedObjectPath(newObjectPath, objectIndex),
                objectNotation)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, addedWithNewName)

        return EventAndNotation(
                RenamedObjectEvent(objectLocation, newName),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun upsertAttribute(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            attributeNotation: AttributeNotation
    ): EventAndNotation {
        val packageNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                AttributePath.ofAttribute(attributeName), attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                UpsertedAttributeEvent(objectLocation, attributeName, attributeNotation),
                nextState)
    }


    private fun updateInAttribute(
            objectLocation: ObjectLocation,
            attributeNesting: AttributePath,
            attributeNotation: AttributeNotation
    ): EventAndNotation {
        val packageNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                attributeNesting, attributeNotation)

        val modifiedProjectNotation = packageNotation.withModifiedObject(
                objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                UpdatedInAttributeEvent(objectLocation, attributeNesting, attributeNotation),
                nextState)
    }


    private fun insertListItemInAttribute(
            objectLocation: ObjectLocation,
            containingList: PositionedAttributeNesting,
            item: AttributeNotation
    ): EventAndNotation {
        val bundleNotation = state.bundleNotations.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val listInAttribute = objectNotation.get(containingList.attributePath) as ListAttributeNotation
        val listWithInsert = listInAttribute.insert(item, containingList.positionIndex)

        val modifiedObjectNotation = objectNotation.upsertAttribute(
                containingList.attributePath, listWithInsert)

        val modifiedBundleNotation = bundleNotation.withModifiedObject(
                objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedBundle(
                objectLocation.bundlePath, modifiedBundleNotation)

        return EventAndNotation(
                InsertedListItemInAttributeEvent(objectLocation, containingList, item),
                nextState)
    }


    private fun insertObjectInListAttribute(
            containingObjectLocation: ObjectLocation,
            containingListPosition: PositionedAttributeNesting,
            objectName: ObjectName,
            positionInBundle: PositionIndex,
            body: ObjectNotation
    ): EventAndNotation {
        val builder = NotationAggregate(state)

        val objectPath = containingObjectLocation.objectPath.nest(
                containingListPosition.attributePath, objectName)

        val objectLocation = ObjectLocation(containingObjectLocation.bundlePath, objectPath)

        val objectAdded = builder
                .apply(AddObjectCommand(PositionedObjectLocation(objectLocation, positionInBundle), body))
                as AddedObjectEvent

        val addendReference = objectLocation.toReference().crop(true, false)
        val insertInAttributeCommand = InsertListItemInAttributeCommand(
                containingObjectLocation,
                containingListPosition,
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