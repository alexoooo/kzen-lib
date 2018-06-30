package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ParameterNotation



sealed class ProjectCommand



data class EditParameterCommand(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation
): ProjectCommand()


