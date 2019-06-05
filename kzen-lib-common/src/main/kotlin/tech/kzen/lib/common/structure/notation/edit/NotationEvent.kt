package tech.kzen.lib.common.structure.notation.edit

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex


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
        val documentNotation: DocumentNotation
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


data class RemovedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath
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


data class InsertedMapEntryInAttributeEvent(
        override val objectLocation: ObjectLocation,
        val containingMap: AttributePath,
        val indexInMap: PositionIndex,
        val key: AttributeSegment,
        val item: AttributeNotation
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
        val removedObject: RemovedObjectEvent
): CompoundNotationEvent(
        listOf(removedInAttribute, removedObject)
) {
    override val documentPath: DocumentPath
        get() = removedInAttribute.documentPath
}


//---------------------------------------------------------------------------------------------------------------------
data class RenamedObjectRefactorEvent(
        val renamedObject: RenamedObjectEvent,
        val adjustedReferences: List<UpdatedInAttributeEvent>
): CompoundNotationEvent(
        listOf(renamedObject).plus(adjustedReferences)
) {
    override val documentPath: DocumentPath
        get() {
            check(adjustedReferences.all { renamedObject.documentPath == it.documentPath })
            return renamedObject.documentPath
        }
}


data class RenamedDocumentRefactorEvent(
        val createdWithNewName: CopiedDocumentEvent,
        val removedUnderOldName: DeletedDocumentEvent
): CompoundNotationEvent(
        listOf(createdWithNewName, removedUnderOldName)
) {
    override val documentPath: DocumentPath
        get() = removedUnderOldName.documentPath
}