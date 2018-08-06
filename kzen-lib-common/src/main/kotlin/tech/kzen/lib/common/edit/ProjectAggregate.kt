package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath


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
                is EditParameterCommand ->
                    editParameter(command.objectName, command.parameterPath, command.parameterValue)

                is AddObjectCommand ->
                    addObject(command.projectPath, command.objectName, command.body)

                is RemoveObjectCommand ->
                    removeObject(command.objectName)

                is ShiftObjectCommand ->
                    shiftObject(command.objectName, command.indexInPackage)

                is RenameObjectCommand ->
                    renameObject(command.objectName, command.newName)

                else ->
                    throw UnsupportedOperationException("Unknown: $command")
            }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            projectPath: ProjectPath,
            objectName: String,
            body: ObjectNotation
    ): EventAndNotation {
        check(! state.coalesce.containsKey(objectName))

        val packageNotation = state.packages[projectPath]!!

        val modifiedProjectNotation =
                packageNotation.withNewObject(objectName, body)

        val nextState = state.withPackage(
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

        val nextState = state.withPackage(
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

        val nextState = state.withPackage(
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

        val nextState = state.withPackage(
                projectPath, addedWithNewName)

        return EventAndNotation(
                ObjectRenamedEvent(objectName, newName),
                nextState)
    }


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

        val nextState = state.withPackage(
                projectPath, modifiedProjectNotation)

        return EventAndNotation(
                ParameterEditedEvent(objectName, parameterPath, parameterValue),
                nextState)
    }
}