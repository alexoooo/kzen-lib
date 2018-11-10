package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.*


class ProjectAggregate(
        var state: ProjectNotation
) {
    //-----------------------------------------------------------------------------------------------------------------
    private data class EventAndNotation(
            val event: ProjectEvent,
            val notation: ProjectNotation)


    //-----------------------------------------------------------------------------------------------------------------
    fun apply(command: ProjectCommand): ProjectEvent {
        val eventAndNotation = handle(command)
        state = eventAndNotation.notation
        return eventAndNotation.event
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handle(command: ProjectCommand): EventAndNotation =
            when (command) {
                is CreatePackageCommand ->
                    createPackage(command.projectPath)

                is DeletePackageCommand ->
                    deletePackage(command.projectPath)


                is AddObjectCommand ->
                    addObject(command.projectPath, command.objectName, command.body, command.index)

                is RemoveObjectCommand ->
                    removeObject(command.objectName)

                is ShiftObjectCommand ->
                    shiftObject(command.objectName, command.indexInPackage)

                is RenameObjectCommand ->
                    renameObject(command.objectName, command.newName)


                is EditParameterCommand ->
                    editParameter(command.objectName, command.parameterPath, command.parameterValue)


//                else ->
//                    throw UnsupportedOperationException("Unknown: $command")
            }




    //-----------------------------------------------------------------------------------------------------------------
    private fun createPackage(
            projectPath: ProjectPath
    ): EventAndNotation {
        check(! state.packages.containsKey(projectPath)) {"Already exists: $projectPath"}

        val nextState = state.withNewPackage(
                projectPath, PackageNotation.empty)

        return EventAndNotation(
                PackageCreatedEvent(projectPath),
                nextState)
    }


    private fun deletePackage(
            projectPath: ProjectPath
    ): EventAndNotation {
        check(state.packages.containsKey(projectPath)) {"Does not exist: $projectPath"}

        val nextState = state.withoutPackage(projectPath)

        return EventAndNotation(
                PackageDeletedEvent(projectPath),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            projectPath: ProjectPath,
            objectName: String,
            body: ObjectNotation,
            index: Int
    ): EventAndNotation {
        check(! state.coalesce.containsKey(objectName)) {"Object named '$objectName' already exists"}

        val packageNotation = state.packages[projectPath]!!

        val modifiedProjectNotation =
                packageNotation.withNewObject(objectName, body, index)

        val nextState = state.withModifiedPackage(
                projectPath, modifiedProjectNotation)

        return EventAndNotation(
                ObjectAddedEvent(projectPath, objectName, body),
                nextState)
    }


    private fun removeObject(
            objectName: String
    ): EventAndNotation {
        check(state.coalesce.containsKey(objectName))

        val projectPath = state.findPackage(objectName)

        val packageNotation = state.packages[projectPath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(objectName)

        val nextState = state.withModifiedPackage(
                projectPath, modifiedProjectNotation)

        return EventAndNotation(
                ObjectRemovedEvent(objectName),
                nextState)
    }


    private fun shiftObject(
            objectName: String,
            indexInPackage: Int
    ): EventAndNotation {
        check(state.coalesce.containsKey(objectName))

        val projectPath = state.findPackage(objectName)

        val packageNotation = state.packages[projectPath]!!

        val objectNotation = state.coalesce[objectName]!!

        val removedFromCurrent =
                packageNotation.withoutObject(objectName)

        val addedToNew =
                removedFromCurrent.withNewObject(objectName, objectNotation, indexInPackage)

        val nextState = state.withModifiedPackage(
                projectPath, addedToNew)

        return EventAndNotation(
                ObjectShiftedEvent(objectName, indexInPackage),
                nextState)
    }


    private fun renameObject(
            objectName: String,
            newName: String
    ): EventAndNotation {
        check(state.coalesce.containsKey(objectName))

        val projectPath = state.findPackage(objectName)

        val packageNotation = state.packages[projectPath]!!

        val objectNotation = state.coalesce[objectName]!!
        val objectIndex = packageNotation.indexOf(objectName)

        val removedCurrentName =
                packageNotation.withoutObject(objectName)

        val addedWithNewName =
                removedCurrentName.withNewObject(newName, objectNotation, objectIndex)

        val nextState = state.withModifiedPackage(
                projectPath, addedWithNewName)

        return EventAndNotation(
                ObjectRenamedEvent(objectName, newName),
                nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun editParameter(
            objectName: String,
            parameterPath: String,
            parameterValue: ParameterNotation
    ): EventAndNotation {
        val projectPath = state.findPackage(objectName)
        val packageNotation = state.packages[projectPath]!!

        val objectNotation = state.coalesce[objectName]!!

        val modifiedObjectNotation =
                objectNotation.upsertParameter(parameterPath, parameterValue)

        val modifiedProjectNotation =
                packageNotation.withModifiedObject(objectName, modifiedObjectNotation)

        val nextState = state.withModifiedPackage(
                projectPath, modifiedProjectNotation)

        return EventAndNotation(
                ParameterEditedEvent(objectName, parameterPath, parameterValue),
                nextState)
    }
}