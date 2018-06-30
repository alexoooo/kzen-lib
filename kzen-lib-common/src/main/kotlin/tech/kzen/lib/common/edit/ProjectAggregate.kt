package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation


class ProjectAggregate(
        var state: ProjectNotation
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun apply(command: ProjectCommand): ProjectEvent {
        val event = handle(command)
        state = event.state
        return event
    }


    private fun handle(command: ProjectCommand): ProjectEvent =
            when (command) {
                is EditParameterCommand ->
                    editParameter(command.objectName, command.parameterPath, command.parameterValue)
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
                packageNotation.withObject(objectName, modifiedObjectNotation)

        val nextState = state.withPackage(
                projectPath, modifiedProjectNotation)

        return ParameterEditedEvent(objectName, parameterPath, parameterValue, nextState)
    }
}