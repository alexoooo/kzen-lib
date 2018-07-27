package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath


class ProjectAggregate(
        var state: ProjectNotation
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun apply(command: ProjectCommand): ProjectEvent {
        val event = handle(command)
        state = event.state
        return event
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun handle(command: ProjectCommand): ProjectEvent =
            when (command) {
                is EditParameterCommand ->
                    editParameter(command.objectName, command.parameterPath, command.parameterValue)

                is AddObjectCommand ->
                    addObject(command.projectPath, command.objectName, command.body)

                is RemoveObjectCommand ->
                    removeObject(command.objectName)

                else ->
                    throw UnsupportedOperationException("Unknown: $command")
            }


    //-----------------------------------------------------------------------------------------------------------------
    private fun addObject(
            projectPath: ProjectPath,
            objectName: String,
            body: ObjectNotation
    ): ObjectAddedEvent {
        check(! state.coalesce.containsKey(objectName))

        val packageNotation = state.packages[projectPath]!!

        val modifiedProjectNotation =
                packageNotation.withNewObject(objectName, body)

        val nextState = state.withPackage(
                projectPath, modifiedProjectNotation)

        return ObjectAddedEvent(projectPath, objectName, body, nextState)
    }


    private fun removeObject(
            objectName: String
    ): ObjectRemovedEvent {
        check(state.coalesce.containsKey(objectName))

        val projectPath = state.findPackage(objectName)

        val packageNotation = state.packages[projectPath]!!

        val modifiedProjectNotation =
                packageNotation.withoutObject(objectName)

        val nextState = state.withPackage(
                projectPath, modifiedProjectNotation)

        return ObjectRemovedEvent(objectName, nextState)
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun editParameter(
            objectName: String,
            parameterPath: String,
            parameterValue: ParameterNotation
    ): ParameterEditedEvent {
        val projectPath = state.findPackage(objectName)
        val packageNotation = state.packages[projectPath]!!

        val objectNotation = state.coalesce[objectName]!!

        val modifiedObjectNotation =
                objectNotation.withParameter(parameterPath, parameterValue)

        val modifiedProjectNotation =
                packageNotation.withModifiedObject(objectName, modifiedObjectNotation)

        val nextState = state.withPackage(
                projectPath, modifiedProjectNotation)

        return ParameterEditedEvent(objectName, parameterPath, parameterValue, nextState)
    }
}