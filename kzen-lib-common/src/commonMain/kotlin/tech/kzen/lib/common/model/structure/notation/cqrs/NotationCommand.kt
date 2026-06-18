package tech.kzen.lib.common.model.structure.notation.cqrs

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.util.ImmutableByteArray


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


// A pure folder (markerless directory) — NOT a document. documentPath.form must be DocumentForm.Folder.
data class CreateFolderCommand(
    val documentPath: DocumentPath
): StructuralNotationCommand()


// Cascade: removes the folder and everything nested under it (see NotationReducer.deleteFolder).
data class DeleteFolderCommand(
    val documentPath: DocumentPath
): StructuralNotationCommand()


data class CopyDocumentCommand(
    val sourceDocumentPath: DocumentPath,
    val destinationDocumentPath: DocumentPath
): StructuralNotationCommand()


data class SetDocumentObjectsCommand(
    val documentPath: DocumentPath,
    val documentObjectNotation: DocumentObjectNotation
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectCommand(
    val objectLocation: ObjectLocation,
    val indexInDocument: PositionRelation,
    val body: ObjectNotation
):
    StructuralNotationCommand()
{
    companion object {
        fun ofParent(
            objectLocation: ObjectLocation,
            indexInDocument: PositionRelation,
            parentName: ObjectName
        ): AddObjectCommand {
            return ofParent(
                objectLocation,
                indexInDocument,
                ObjectReference.ofRootName(parentName))
        }


        fun ofParent(
            objectLocation: ObjectLocation,
            indexInDocument: PositionRelation,
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
    val newPositionInDocument: PositionRelation
): StructuralNotationCommand()


// Moves an object together with its whole nested subtree (every descendant object) to a new document
// position, keeping the subtree contiguous and preserving its internal order. newPositionInDocument is
// resolved as an insertion index against the document with the subtree removed.
data class ShiftObjectTreeCommand(
    val objectLocation: ObjectLocation,
    val newPositionInDocument: PositionRelation
): StructuralNotationCommand()


data class RenameObjectCommand(
    val objectLocation: ObjectLocation,
    val newName: ObjectName
): StructuralNotationCommand()


data class RenameNestedObjectCommand(
    val objectLocation: ObjectLocation,
    val newObjectNesting: ObjectNesting
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class UpsertAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val attributeNotation: AttributeNotation
): StructuralNotationCommand()


data class UpdateInAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributePath: AttributePath,
    val attributeNotation: AttributeNotation
): StructuralNotationCommand()


data class UpdateAllNestingsInAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val attributeNestings: List<AttributeNesting>,
    val attributeNotation: AttributeNotation
): StructuralNotationCommand()


data class UpdateAllValuesInAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val nestingNotations: Map<AttributeNesting, AttributeNotation>
): StructuralNotationCommand()


data class InsertListItemInAttributeCommand(
    val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val indexInList: PositionRelation,
    val item: AttributeNotation
): StructuralNotationCommand()


data class InsertAllListItemsInAttributeCommand(
    val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val indexInList: PositionRelation,
    val items: List<AttributeNotation>
): StructuralNotationCommand()


// TODO: add UpsertMapEntryInAttributeCommand using AttributeTypedLocation instead of createAncestorsIfAbsent?
data class InsertMapEntryInAttributeCommand(
    val objectLocation: ObjectLocation,
    val containingMap: AttributePath,
    val indexInMap: PositionRelation,
    val mapKey: AttributeSegment,
    val value: AttributeNotation,
    val createAncestorsIfAbsent: Boolean
): StructuralNotationCommand()


data class RemoveInAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributePath: AttributePath,
    val removeContainerIfEmpty: Boolean
): StructuralNotationCommand()


data class RemoveListItemInAttributeCommand(
    val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val item: AttributeNotation,
    val removeContainerIfEmpty: Boolean
): StructuralNotationCommand()


data class RemoveAllListItemsInAttributeCommand(
    val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val items: List<AttributeNotation>,
    val removeContainerIfEmpty: Boolean
): StructuralNotationCommand()


data class ShiftInAttributeCommand(
    val objectLocation: ObjectLocation,
    val attributePath: AttributePath,
    val newPosition: PositionRelation
): StructuralNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddObjectAtAttributeCommand(
    val containingObjectLocation: ObjectLocation,
    val containingAttribute: AttributeName,
    val objectName: ObjectName,
    val positionInDocument: PositionRelation,
    val objectNotation: ObjectNotation
):
    StructuralNotationCommand()
{
    fun insertedObjectLocation(): ObjectLocation {
        val objectPath = containingObjectLocation.objectPath.nest(AttributePath.ofName(containingAttribute), objectName)
        return ObjectLocation(containingObjectLocation.documentPath, objectPath)
    }
}


data class InsertObjectInListAttributeCommand(
    val containingObjectLocation: ObjectLocation,
    val containingList: AttributePath,
    val indexInList: PositionRelation,
    val objectName: ObjectName,
    val positionInDocument: PositionRelation,
    val objectNotation: ObjectNotation
):
    StructuralNotationCommand()
{
    fun insertedObjectLocation(): ObjectLocation {
        val objectPath = containingObjectLocation.objectPath.nest(containingList, objectName)
        return ObjectLocation(containingObjectLocation.documentPath, objectPath)
    }
}


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


// Rename a folder: re-nests the folder and its whole subtree, rewriting references into it (see
// NotationReducer.relocateFolderRefactor). documentPath.form must be DocumentForm.Folder.
data class RenameFolderRefactorCommand(
    val documentPath: DocumentPath,
    val newName: DocumentName
): SemanticNotationCommand()


// Move a document under a different parent folder (newNesting = destination folder's content nesting),
// rewriting references into it (see NotationReducer.relocateDocumentRefactor).
data class MoveDocumentRefactorCommand(
    val documentPath: DocumentPath,
    val newNesting: DocumentNesting
): SemanticNotationCommand()


// Move a folder (and its whole subtree) under a different parent folder (newNesting = destination folder's
// content nesting), rewriting references into it (see NotationReducer.relocateFolderRefactor).
data class MoveFolderRefactorCommand(
    val documentPath: DocumentPath,
    val newNesting: DocumentNesting
): SemanticNotationCommand()


// Re-parent an object together with its whole nested subtree (every descendant object) into a different
// containing branch (newObjectNesting = the root's nesting in the destination branch) and reposition it.
// Re-nests root + descendants (prefix-rewrite), rewrites references into the subtree, and shifts the
// contiguous subtree to newPositionInDocument (resolved against the document with the subtree removed).
// Rejects re-parenting into the subtree's own descendants (see NotationReducer.relocateObjectTreeRefactor).
data class RelocateObjectTreeRefactorCommand(
    val objectLocation: ObjectLocation,
    val newObjectNesting: ObjectNesting,
    val newPositionInDocument: PositionRelation
): SemanticNotationCommand()


//---------------------------------------------------------------------------------------------------------------------
data class AddResourceCommand(
    val resourceLocation: ResourceLocation,
    val resourceContent: ImmutableByteArray
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

