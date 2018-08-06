package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.*


//---------------------------------------------------------------------------------------------------------------------
sealed class ProjectCommand



//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val projectPath: ProjectPath,
        val objectName: String,
        val body: ObjectNotation
): ProjectCommand() {
    companion object {
        fun ofParent(projectPath: ProjectPath, objectName: String, parentName: String): AddObjectCommand {
            val parentBody = ObjectNotation(mapOf(
                    ParameterConventions.isParameter to ScalarParameterNotation(parentName)))
            return AddObjectCommand(projectPath, objectName, parentBody)
        }
    }
}



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