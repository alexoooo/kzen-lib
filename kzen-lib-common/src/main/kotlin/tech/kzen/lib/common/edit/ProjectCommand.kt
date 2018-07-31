package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectPath


//---------------------------------------------------------------------------------------------------------------------
sealed class ProjectCommand



//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val projectPath: ProjectPath,
        val objectName: String,
        val body: ObjectNotation
): ProjectCommand()



data class RemoveObjectCommand(
        val objectName: String
): ProjectCommand()



data class ShiftObjectCommand(
        val objectName: String,
        val indexInPackage: Int
): ProjectCommand()



data class EditParameterCommand(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation
): ProjectCommand()



data class RenameObjectCommand(
        val objectName: String,
        val newName: String
): ProjectCommand()



//---------------------------------------------------------------------------------------------------------------------
data class CreatePackageCommand(
        val projectPath: ProjectPath
): ProjectCommand()



data class DeletePackageCommand(
        val projectPath: ProjectPath
): ProjectCommand()