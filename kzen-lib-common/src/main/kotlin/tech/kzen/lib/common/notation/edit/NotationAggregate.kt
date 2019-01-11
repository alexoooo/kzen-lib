package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.api.model.AttributeNesting
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName


class NotationAggregate(
        var state: NotationTree
) {
    //-----------------------------------------------------------------------------------------------------------------
    private data class EventAndNotation(
            val event: NotationEvent,
            val notation: NotationTree)


    //-----------------------------------------------------------------------------------------------------------------
    fun apply(command: NotationCommand): NotationEvent {
        val eventAndNotation = handle(command)
        state = eventAndNotation.notation
        return eventAndNotation.event
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handle(command: NotationCommand): EventAndNotation =
            when (command) {
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


//                is EditParameterCommand ->
//                    editParameter(command.objectLocation, command.attributeNesting, command.attributeNotation)


                else ->
                    throw UnsupportedOperationException("Unknown: $command")
            }




    //-----------------------------------------------------------------------------------------------------------------
    private fun createPackage(
            projectPath: BundlePath
    ): EventAndNotation {
        check(! state.files.values.containsKey(projectPath)) {"Already exists: $projectPath"}

        val nextState = state.withNewPackage(
                projectPath, BundleNotation.empty)

        return EventAndNotation(
                BundleCreatedEvent(projectPath),
                nextState)
    }


    private fun deletePackage(
            projectPath: BundlePath
    ): EventAndNotation {
        check(state.files.values.containsKey(projectPath)) {"Does not exist: $projectPath"}

        val nextState = state.withoutPackage(projectPath)

        return EventAndNotation(
                BundleDeletedEvent(projectPath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            objectLocation: ObjectLocation,
            indexInBundle: PositionIndex,
            body: ObjectNotation
    ): EventAndNotation {
        check(objectLocation !in state.coalesce.values) {"Object named '$objectLocation' already exists"}

        val packageNotation = state.files.values[objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                packageNotation.withNewObject(
                        PositionedObjectPath(objectLocation.objectPath, indexInBundle),
                        body)

        val nextState = state.withModifiedPackage(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                ObjectAddedEvent(objectLocation, body),
                nextState)
    }


    private fun removeObject(
            objectLocation: ObjectLocation
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val packageNotation = state.files.values[objectLocation.bundlePath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(objectLocation.objectPath)

        val nextState = state.withModifiedPackage(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                ObjectRemovedEvent(objectLocation),
                nextState)
    }


    private fun shiftObject(
            objectLocation: ObjectLocation,
            newPositionInBundle: PositionIndex
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

        val packageNotation = state.files.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val removedFromCurrent = packageNotation.withoutObject(objectLocation.objectPath)

        val addedToNew = removedFromCurrent.withNewObject(
                PositionedObjectPath(objectLocation.objectPath, newPositionInBundle),
                objectNotation)

        val nextState = state.withModifiedPackage(
                objectLocation.bundlePath, addedToNew)

        return EventAndNotation(
                ObjectShiftedEvent(objectLocation, newPositionInBundle),
                nextState)
    }


    private fun renameObject(
            objectLocation: ObjectLocation,
            newName: ObjectName
    ): EventAndNotation {
        check(objectLocation in state.coalesce.values)

//        val projectPath = state.findPackage(objectName)

        val packageNotation = state.files.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)
        val objectIndex = packageNotation.indexOf(objectLocation.objectPath)

        val removedCurrentName =
                packageNotation.withoutObject(objectLocation.objectPath)

        val addedWithNewName = removedCurrentName.withNewObject(
                PositionedObjectPath(objectLocation.objectPath, objectIndex),
                objectNotation)

        val nextState = state.withModifiedPackage(
                objectLocation.bundlePath, addedWithNewName)

        return EventAndNotation(
                ObjectRenamedEvent(objectLocation, newName),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun editParameter(
            objectLocation: ObjectLocation,
            attributeNesting: AttributeNesting,
            attributeNotation: AttributeNotation
    ): EventAndNotation {
//        val projectPath = state.findPackage(objectName)
        val packageNotation = state.files.values[objectLocation.bundlePath]!!

        val objectNotation = state.coalesce.get(objectLocation)

        val modifiedObjectNotation =
                objectNotation.upsertParameter(attributeNesting, attributeNotation)

        val modifiedProjectNotation =
                packageNotation.withModifiedObject(objectLocation.objectPath, modifiedObjectNotation)

        val nextState = state.withModifiedPackage(
                objectLocation.bundlePath, modifiedProjectNotation)

        return EventAndNotation(
                ParameterEditedEvent(objectLocation, attributeNesting, attributeNotation),
                nextState)
    }
}