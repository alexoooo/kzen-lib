package tech.kzen.lib.common.model.structure.notation.cqrs

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.resource.ResourceContent


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationCommand


sealed class StructuralNotationCommand: NotationCommand()
sealed class SemanticNotationCommand: NotationCommand()
sealed class ResourceNotationCommand: StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class CreateDocumentCommand(
        val documentPath: DocumentPath,
        val documentObjectNotation: DocumentObjectNotation
): StructuralNotationCommand()


data class DeleteDocumentCommand(
        val documentPath: DocumentPath
): StructuralNotationCommand()


data class CopyDocumentCommand(
        val sourceDocumentPath: DocumentPath,
        val destinationDocumentPath: DocumentPath
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
        val objectLocation: ObjectLocation,
        val indexInDocument: PositionIndex,
        val body: ObjectNotation
): StructuralNotationCommand() {
    companion object {
        fun ofParent(
                objectLocation: ObjectLocation,
                indexInDocument: PositionIndex,
                parentName: ObjectName
        ): AddObjectCommand {
            return ofParent(
                    objectLocation,
                    indexInDocument,
                    ObjectReference.ofRootName(parentName))
        }


        fun ofParent(
                objectLocation: ObjectLocation,
                indexInDocument: PositionIndex,
                parentReference: ObjectReference
        ): AddObjectCommand {
            val parentBody = ObjectNotation.ofParent(parentReference)
            return AddObjectCommand(objectLocation, indexInDocument, parentBody)
        }
    }
}


data class RemoveObjectCommand(
        val objectLocation: ObjectLocation
): StructuralNotationCommand()


data class ShiftObjectCommand(
        val objectLocation: ObjectLocation,
        val newPositionInDocument: PositionIndex
): StructuralNotationCommand()


data class RenameObjectCommand(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): StructuralNotationCommand()


data class RenameNestedObjectCommand(
        val objectLocation: ObjectLocation,
        val newObjectNesting: ObjectNesting
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
        val mapKey: AttributeSegment,
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
        val positionInDocument: PositionIndex,
        val body: ObjectNotation
): StructuralNotationCommand()


data class RemoveObjectInAttributeCommand(
        val containingObjectLocation: ObjectLocation,
        val attributePath: AttributePath
): StructuralNotationCommand()


//data class InsertObjectInMapAttributeCommand(
//        val objectLocation: PositionedObjectLocation,
//        val containingMapPosition: PositionedAttributeNesting,
//        val key: MapKeyAttributeSegment,
//        val body: ObjectNotation
//): ProjectCommand()


data class RenameObjectRefactorCommand(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): SemanticNotationCommand()


data class RenameDocumentRefactorCommand(
        val documentPath: DocumentPath,
        val newName: DocumentName
): SemanticNotationCommand()


//data class MoveRefactorCommand(
//        val objectLocation: ObjectLocation,
//        val newName: ObjectName
//): ProjectCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddResourceCommand(
        val resourceLocation: ResourceLocation,
        val resourceContent: ResourceContent
): ResourceNotationCommand()


data class RemoveResourceCommand(
        val resourceLocation: ResourceLocation
): ResourceNotationCommand()


//data class ReplaceResourceCommand(
//        val resourceLocation: ResourceLocation,
//        val newResourceContent: ResourceContent
//): ResourceNotationCommand()
//
//
//data class RenameResourceCommand(
//        val resourceLocation: ResourceLocation,
//        val newName: ResourceName
//): ResourceNotationCommand()
//
//
//data class MoveResourceCommand(
//        val resourceLocation: ResourceLocation,
//        val newPath: ResourcePath
//): ResourceNotationCommand()

