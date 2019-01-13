package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.api.model.*


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationCommand



//---------------------------------------------------------------------------------------------------------------------
data class CreateBundleCommand(
        val bundlePath: BundlePath
): NotationCommand()



data class DeletePackageCommand(
        val filePath: BundlePath
): NotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val location: PositionedObjectLocation,
        val body: ObjectNotation
): NotationCommand() {
    companion object {
        fun ofParent(
                location: PositionedObjectLocation,
                parentName: ObjectName
        ): AddObjectCommand {
            val parentBody = ObjectNotation(mapOf(
                    AttributeName(NotationConventions.isAttribute)
                            to ScalarAttributeNotation(parentName)))
            return AddObjectCommand(location, parentBody)
        }
    }
}


data class RemoveObjectCommand(
        val location: ObjectLocation
): NotationCommand()


data class ShiftObjectCommand(
        val location: ObjectLocation,
        val newPositionInBundle: PositionIndex
): NotationCommand()


data class RenameObjectCommand(
        val location: ObjectLocation,
        val newName: ObjectName
): NotationCommand()


data class RelocateObjectCommand(
        val location: ObjectLocation,
        val newObjectPath: PositionedObjectPath
): NotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class UpsertAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val attributeNotation: AttributeNotation
): NotationCommand()


data class ClearAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName
): NotationCommand()


data class UpdateInAttributeCommand(
        val objectLocation: ObjectLocation,
        val attributeNesting: AttributeNesting,
        val attributeNotation: AttributeNotation
): NotationCommand()


data class InsertListItemInAttributeCommand(
        val objectLocation: ObjectLocation,
        val containingList: PositionedAttributeNesting,
        val item: AttributeNotation
): NotationCommand()


data class InsertMapEntryInAttributeCommand(
        val objectLocation: ObjectLocation,
        val containingMap: PositionedAttributeNesting,
        val key: AttributeSegment,
        val value: AttributeNotation
): NotationCommand()


data class ShiftInAttributeCommand(
        val objectLocation: ObjectLocation,
        val containingStructure: PositionedAttributeNesting
): NotationCommand()



//---------------------------------------------------------------------------------------------------------------------
data class InsertObjectInListAttributeCommand(
        val containingObjectLocation: ObjectLocation,
        val containingListPosition: PositionedAttributeNesting,
        val objectLocation: PositionedObjectLocation,
        val body: ObjectNotation
): NotationCommand()


// TODO: could use __REF__ or inline object definition
//data class InsertObjectInMapAttributeCommand(
//        val objectLocation: PositionedObjectLocation,
//        val containingMapPosition: PositionedAttributeNesting,
//        val key: MapKeyAttributeSegment,
//        val body: ObjectNotation
//): ProjectCommand()


data class RenameRefactorCommand(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): NotationCommand()


//data class MoveRefactorCommand(
//        val objectLocation: ObjectLocation,
//        val newName: ObjectName
//): ProjectCommand()
