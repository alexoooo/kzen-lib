package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.model.PositionedObjectPath


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationCommand


sealed class StructuralNotationCommand: NotationCommand()
sealed class SemanticNotationCommand: NotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class CreateBundleCommand(
        val bundlePath: BundlePath
): StructuralNotationCommand()



data class DeletePackageCommand(
        val bundlePath: BundlePath
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val objectLocation: ObjectLocation,
        val indexInBundle: PositionIndex,
        val body: ObjectNotation
): StructuralNotationCommand() {
    companion object {
        fun ofParent(
                objectLocation: ObjectLocation,
                indexInBundle: PositionIndex,
                parentName: ObjectName
        ): AddObjectCommand {
            return ofParent(
                    objectLocation,
                    indexInBundle,
                    ObjectReference.ofName(parentName))
        }


        fun ofParent(
                objectLocation: ObjectLocation,
                indexInBundle: PositionIndex,
                parentReference: ObjectReference
        ): AddObjectCommand {
            val parentBody = ObjectNotation.ofParent(parentReference.asString())
            return AddObjectCommand(objectLocation, indexInBundle, parentBody)
        }
    }
}


data class RemoveObjectCommand(
        val objectLocation: ObjectLocation
): StructuralNotationCommand()


data class ShiftObjectCommand(
        val objectLocation: ObjectLocation,
        val newPositionInBundle: PositionIndex
): StructuralNotationCommand()


data class RenameObjectCommand(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): StructuralNotationCommand()


data class RelocateObjectCommand(
        val location: ObjectLocation,
        val newObjectPath: PositionedObjectPath
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class UpsertAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val attributeNotation: AttributeNotation
): StructuralNotationCommand()


data class ClearAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName
): StructuralNotationCommand()


data class UpdateInAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath,
        val attributeNotation: AttributeNotation
): StructuralNotationCommand()


data class InsertListItemInAttributeCommand(
        val objectLocation: ObjectLocation,
        val containingList: AttributePath,
        val indexInList: PositionIndex,
        val item: AttributeNotation
): StructuralNotationCommand()


data class InsertMapEntryInAttributeCommand(
        val objectLocation: ObjectLocation,
        val containingMap: AttributePath,
        val indexInMap: PositionIndex,
        val key: AttributeSegment,
        val value: AttributeNotation
): StructuralNotationCommand()


data class RemoveInAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath
): StructuralNotationCommand()


data class ShiftInAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath,
        val newPosition: PositionIndex
): StructuralNotationCommand()



//---------------------------------------------------------------------------------------------------------------------
data class InsertObjectInListAttributeCommand(
        val containingObjectLocation: ObjectLocation,
        val containingList: AttributePath,
        val indexInList: PositionIndex,
        val objectName: ObjectName,
        val positionInBundle: PositionIndex,
        val body: ObjectNotation
): StructuralNotationCommand()


// TODO: could use __REF__ or inline object definition?
//data class InsertObjectInMapAttributeCommand(
//        val objectLocation: PositionedObjectLocation,
//        val containingMapPosition: PositionedAttributeNesting,
//        val key: MapKeyAttributeSegment,
//        val body: ObjectNotation
//): ProjectCommand()


data class RenameRefactorCommand(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): SemanticNotationCommand()


//data class MoveRefactorCommand(
//        val objectLocation: ObjectLocation,
//        val newName: ObjectName
//): ProjectCommand()
