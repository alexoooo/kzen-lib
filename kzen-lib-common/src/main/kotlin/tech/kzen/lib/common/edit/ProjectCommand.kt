package tech.kzen.lib.common.edit

import tech.kzen.lib.common.notation.model.*


//---------------------------------------------------------------------------------------------------------------------
sealed class ProjectCommand




//---------------------------------------------------------------------------------------------------------------------
data class CreatePackageCommand(
        val projectPath: ProjectPath
): ProjectCommand()



data class DeletePackageCommand(
        val projectPath: ProjectPath
): ProjectCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val projectPath: ProjectPath,
        val objectName: String,
        val body: ObjectNotation,
        val index: Int
): ProjectCommand() {
    companion object {
        fun ofParent(
                projectPath: ProjectPath,
                objectName: String,
                parentName: String,
                index: Int
        ): AddObjectCommand {
            val parentBody = ObjectNotation(mapOf(
                    ParameterConventions.isParameter to ScalarParameterNotation(parentName)))
            return AddObjectCommand(projectPath, objectName, parentBody, index)
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



data class RenameObjectCommand(
        val objectName: String,
        val newName: String
): ProjectCommand()


//---------------------------------------------------------------------------------------------------------------------
data class EditParameterCommand(
        val objectName: String,
        val parameterPath: String,
        val parameterValue: ParameterNotation
): ProjectCommand()

