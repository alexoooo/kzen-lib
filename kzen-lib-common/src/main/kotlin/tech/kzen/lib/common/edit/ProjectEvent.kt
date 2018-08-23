package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectPath


//---------------------------------------------------------------------------------------------------------------------
sealed class ProjectEvent



//---------------------------------------------------------------------------------------------------------------------
data class PackageCreatedEvent(
        val projectPath: ProjectPath
) : ProjectEvent()



data class PackageDeletedEvent(
        val projectPath: ProjectPath
) : ProjectEvent()



//---------------------------------------------------------------------------------------------------------------------
data class ObjectAddedEvent(
        val projectPath: ProjectPath,
        val objectName: String,
        val body: ObjectNotation
) : ProjectEvent()



data class ObjectRemovedEvent(
        val objectName: String
) : ProjectEvent()



data class ObjectShiftedEvent(
        val objectName: String,
        val indexInPackage: Int
) : ProjectEvent()



data class ObjectRenamedEvent(
        val objectName: String,
        val newName: String
) : ProjectEvent()


//---------------------------------------------------------------------------------------------------------------------
data class ParameterEditedEvent(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation
) : ProjectEvent()
