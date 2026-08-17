package tech.kzen.lib.common.model.structure.notation.cqrs

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.util.digest.Digest


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationEvent {
    abstract val documentPath: DocumentPath
}


sealed class SingularNotationEvent: NotationEvent()


sealed class CompoundNotationEvent(
    @Suppress("unused")
    val singularEvents: List<SingularNotationEvent>
): NotationEvent()


// An event addressed to one object, which is where its document comes from.
sealed class ObjectNotationEvent: SingularNotationEvent() {
    abstract val objectLocation: ObjectLocation

    override val documentPath: DocumentPath
        get() = objectLocation.documentPath
}


//---------------------------------------------------------------------------------------------------------------------
data class CreatedDocumentEvent(
    override val documentPath: DocumentPath,
    val documentNotation: DocumentObjectNotation
): SingularNotationEvent()


data class DeletedDocumentEvent(
    override val documentPath: DocumentPath
): SingularNotationEvent()


data class CreatedFolderEvent(
    override val documentPath: DocumentPath
): SingularNotationEvent()


// NB: the cascade-removed descendants are reflected in the resulting GraphNotation, not enumerated here.
data class DeletedFolderEvent(
    override val documentPath: DocumentPath
): SingularNotationEvent()


data class CopiedDocumentEvent(
    override val documentPath: DocumentPath,
    val destination: DocumentPath
): SingularNotationEvent()


data class SetDocumentObjectsEvent(
    override val documentPath: DocumentPath,
    val documentObjectNotation: DocumentObjectNotation
): SingularNotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class AddedObjectEvent(
    override val objectLocation: ObjectLocation,
    val indexInDocument: PositionIndex,
    val objectNotation: ObjectNotation
): ObjectNotationEvent()


data class RemovedObjectEvent(
    override val objectLocation: ObjectLocation
): ObjectNotationEvent()


data class ShiftedObjectEvent(
    override val objectLocation: ObjectLocation,
    val newPositionInDocument: PositionIndex
): ObjectNotationEvent()


// NB: the subtree's repositioned descendants are reflected in the resulting GraphNotation, not enumerated here
// (newPositionInDocument is the resolved insertion index of the subtree root).
data class ShiftedObjectTreeEvent(
    override val objectLocation: ObjectLocation,
    val newPositionInDocument: PositionIndex
): ObjectNotationEvent()


data class RenamedObjectEvent(
    override val objectLocation: ObjectLocation,
    val newName: ObjectName
): ObjectNotationEvent() {
    @Suppress("MemberVisibilityCanBePrivate")
    fun newObjectPath(): ObjectPath {
        return objectLocation.objectPath.copy(name = newName)
    }

    @Suppress("unused")
    fun newObjectLocation(): ObjectLocation {
        return objectLocation.copy(
                objectPath = newObjectPath())
    }
}


data class RenamedNestedObjectEvent(
    override val objectLocation: ObjectLocation,
    val newObjectNesting: ObjectNesting
): ObjectNotationEvent() {
    @Suppress("MemberVisibilityCanBePrivate")
    fun newObjectPath(): ObjectPath {
        return objectLocation.objectPath.copy(nesting = newObjectNesting)
    }

    fun newObjectLocation(): ObjectLocation {
        return objectLocation.copy(
            objectPath = newObjectPath())
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class UpsertedAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val attributeValue: AttributeNotation
): ObjectNotationEvent()


data class UpdatedInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributeNesting: AttributePath,
    val attributeNotation: AttributeNotation
): ObjectNotationEvent()


data class UpdatedAllNestingsInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val attributeNestings: List<AttributeNesting>,
    val attributeNotation: AttributeNotation
): ObjectNotationEvent()


data class UpdatedAllValuesInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributeName: AttributeName,
    val nestingNotations: Map<AttributeNesting, AttributeNotation>
): ObjectNotationEvent()


data class RemovedInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributePath: AttributePath
): ObjectNotationEvent()


data class RemovedAllInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val attributePaths: List<AttributePath>
): ObjectNotationEvent()


//--------------------------------------------------------------
sealed class InsertedInAttributeEvent: ObjectNotationEvent()


data class InsertedListItemInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val indexInList: PositionIndex,
    val item: AttributeNotation
): InsertedInAttributeEvent()


data class InsertedAllListItemsInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val containingList: AttributePath,
    val indexInList: PositionIndex,
    val items: List<AttributeNotation>
): InsertedInAttributeEvent()


data class InsertedMapEntryInAttributeEvent(
    override val objectLocation: ObjectLocation,
    val containingMap: AttributePath,
    val indexInMap: PositionIndex,
    val key: AttributeSegment,
    val item: AttributeNotation,
    val createdAncestors: List<AttributePath>
): InsertedInAttributeEvent()


//--------------------------------------------------------------
data class ShiftedInAttributeEvent(
    val removedInAttribute: RemovedInAttributeEvent,
    val reinsertedInAttribute: InsertedInAttributeEvent
): CompoundNotationEvent(
        listOf(removedInAttribute, reinsertedInAttribute)
) {
    override val documentPath: DocumentPath
        get() = removedInAttribute.documentPath
}


data class AddedObjectAtAttributeEvent(
    val addedObject: AddedObjectEvent,
    val addedInAttribute: UpsertedAttributeEvent
): CompoundNotationEvent(
        listOf(addedObject, addedInAttribute)
) {
    override val documentPath: DocumentPath
        get() = addedObject.documentPath
}


data class InsertedObjectInListAttributeEvent(
    val addedObject: AddedObjectEvent,
    val insertedInAttribute: InsertedListItemInAttributeEvent
): CompoundNotationEvent(
        listOf(addedObject, insertedInAttribute)
) {
    override val documentPath: DocumentPath
        get() = addedObject.documentPath
}


data class RemovedObjectInAttributeEvent(
    val removedInAttribute: RemovedInAttributeEvent,
    val removedObject: RemovedObjectEvent,
    val removedNestedObjects: List<RemovedObjectEvent>
): CompoundNotationEvent(
        listOf(removedInAttribute, removedObject) + removedNestedObjects
) {
    override val documentPath: DocumentPath
        get() = removedInAttribute.documentPath
}


//---------------------------------------------------------------------------------------------------------------------
data class RenamedObjectRefactorEvent(
    val renamedObject: RenamedObjectEvent,
    val adjustedReferences: List<UpdatedInAttributeEvent>,
    val nestedObjectRenames: List<NestedObjectRename>
): CompoundNotationEvent(
        listOf(renamedObject) +
                adjustedReferences +
                nestedObjectRenames.flatMap { it.singularEvents() }
) {
    override val documentPath: DocumentPath
        get() {
            check(singularEvents.all { renamedObject.documentPath == it.documentPath })
            return renamedObject.documentPath
        }
}


data class NestedObjectRename(
    val renamedNestedObject: RenamedNestedObjectEvent,
    val adjustedReferences: List<UpdatedInAttributeEvent>
) {
    fun singularEvents(): List<SingularNotationEvent> {
        return listOf(renamedNestedObject).plus(adjustedReferences)
    }
}


// Re-parent of an object subtree into a different branch + reposition (see
// NotationReducer.relocateObjectTreeRefactor). nestedObjectRenames re-nest the root and every descendant
// (each a RenamedNestedObjectEvent so ObjectStableMapper remaps the stable id) and rewrite references into
// the subtree; shiftedObjectTree repositions the re-nested subtree.
data class RelocatedObjectTreeRefactorEvent(
    val nestedObjectRenames: List<NestedObjectRename>,
    val shiftedObjectTree: ShiftedObjectTreeEvent
): CompoundNotationEvent(
    nestedObjectRenames.flatMap { it.singularEvents() } + shiftedObjectTree
) {
    override val documentPath: DocumentPath
        get() = shiftedObjectTree.documentPath
}


data class RenamedDocumentRefactorEvent(
    val createdWithNewName: CopiedDocumentEvent,
    val removedUnderOldName: DeletedDocumentEvent,
    val adjustedReferences: List<UpdatedInAttributeEvent>
): CompoundNotationEvent(
    listOf(createdWithNewName, removedUnderOldName) +
            adjustedReferences
) {
    override val documentPath: DocumentPath
        get() = removedUnderOldName.documentPath
}


// Emitted by both folder rename and folder move (see NotationReducer.relocateFolderRefactor). createdFolder
// is the new folder path, removedFolder the old (cascade-removed) one; copiedDocuments / createdSubfolders
// relocate the subtree, adjustedReferences rewrite references into it. The copiedDocuments MUST stay in
// singularEvents — DirectGraphStore.writeModified discovers document/resource copies by filtering them out.
data class RenamedFolderRefactorEvent(
    val createdFolder: CreatedFolderEvent,
    val createdSubfolders: List<CreatedFolderEvent>,
    val copiedDocuments: List<CopiedDocumentEvent>,
    val adjustedReferences: List<UpdatedInAttributeEvent>,
    val removedFolder: DeletedFolderEvent
): CompoundNotationEvent(
    listOf(createdFolder) +
            createdSubfolders +
            copiedDocuments +
            adjustedReferences +
            listOf(removedFolder)
) {
    override val documentPath: DocumentPath
        get() = removedFolder.documentPath
}


//---------------------------------------------------------------------------------------------------------------------
data class AddedResourceEvent(
    val resourceLocation: ResourceLocation,
    val contentDigest: Digest
): SingularNotationEvent() {
    override val documentPath
        get() = resourceLocation.documentPath
}


data class RemovedResourceEvent(
    val resourceLocation: ResourceLocation
): SingularNotationEvent() {
    override val documentPath
        get() = resourceLocation.documentPath
}
