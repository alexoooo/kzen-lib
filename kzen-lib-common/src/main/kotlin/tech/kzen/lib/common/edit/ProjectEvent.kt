package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath


//---------------------------------------------------------------------------------------------------------------------
sealed class ProjectEvent {
    abstract val state: ProjectNotation
}



//---------------------------------------------------------------------------------------------------------------------
data class ObjectAddedEvent(
        val projectPath: ProjectPath,
        val objectName: String,
        val body: ObjectNotation,
        override val state: ProjectNotation
) : ProjectEvent()



data class ObjectRemovedEvent(
        val objectName: String,
        override val state: ProjectNotation
) : ProjectEvent()



data class ObjectShiftedEvent(
        val objectName: String,
        val indexInPackage: Int,
        override val state: ProjectNotation
) : ProjectEvent()



data class ObjectRenamedEvent(
        val objectName: String,
        val newName: String,
        override val state: ProjectNotation
) : ProjectEvent()


//---------------------------------------------------------------------------------------------------------------------
data class ParameterEditedEvent(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation,
        override val state: ProjectNotation
) : ProjectEvent()