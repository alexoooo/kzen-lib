package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation



sealed class ProjectEvent {
    abstract val state: ProjectNotation
}



data class ParameterEditedEvent(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation,
        override val state: ProjectNotation
) : ProjectEvent()