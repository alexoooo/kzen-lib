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


//---------------------------------------------------------------------------------------------------------------------
data class CreatedDocumentEvent(
        override val documentPath: DocumentPath,
        val documentNotation: DocumentObjectNotation
): SingularNotationEvent()


data class DeletedDocumentEvent(
        override val documentPath: DocumentPath
): SingularNotationEvent()


data class CopiedDocumentEvent(
        override val documentPath: DocumentPath,
        val destination: DocumentPath
): SingularNotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class AddedObjectEvent(
        val objectLocation: ObjectLocation,
        val indexInDocument: PositionIndex,
        val objectNotation: ObjectNotation
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class RemovedObjectEvent(
        val objectLocation: ObjectLocation
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class ShiftedObjectEvent(
        val objectLocation: ObjectLocation,
        val newPositionInDocument: PositionIndex
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class RenamedObjectEvent(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath


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
        val objectLocation: ObjectLocation,
        val newObjectNesting: ObjectNesting
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath


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
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val attributeValue: AttributeNotation
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class UpdatedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeNesting: AttributePath,
        val attributeNotation: AttributeNotation
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class UpdatedAllNestingsInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val attributeNestings: List<AttributeNesting>,
        val attributeNotation: AttributeNotation
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class UpdatedAllValuesInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val nestingNotations: Map<AttributeNesting, AttributeNotation>
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class RemovedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


data class RemovedAllInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributePaths: List<AttributePath>
): SingularNotationEvent() {
    override val documentPath
        get() = objectLocation.documentPath
}


//--------------------------------------------------------------
sealed class InsertedInAttributeEvent: SingularNotationEvent() {
    abstract val objectLocation: ObjectLocation

    override val documentPath
        get() = objectLocation.documentPath
}


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
