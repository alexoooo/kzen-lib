package tech.kzen.lib.common.structure.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationEvent


sealed class SingularNotationEvent: NotationEvent()


sealed class CompoundNotationEvent(
        val singularEvents: List<SingularNotationEvent>
): NotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class CreatedDocumentEvent(
        val documentPath: DocumentPath,
        val documentNotation: DocumentNotation
): SingularNotationEvent()



data class DeletedDocumentEvent(
        val documentPath: DocumentPath
): SingularNotationEvent()


data class CopiedDocumentEvent(
        val documentPath: DocumentPath,
        val destination: DocumentPath
): SingularNotationEvent()



//---------------------------------------------------------------------------------------------------------------------
data class AddedObjectEvent(
        val objectLocation: ObjectLocation,
        val indexInDocument: PositionIndex,
        val objectNotation: ObjectNotation
): SingularNotationEvent()



data class RemovedObjectEvent(
        val objectLocation: ObjectLocation
): SingularNotationEvent()



data class ShiftedObjectEvent(
        val objectLocation: ObjectLocation,
        val newPositionInDocument: PositionIndex
): SingularNotationEvent()



data class RenamedObjectEvent(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): SingularNotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class UpsertedAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeName: AttributeName,
        val attributeValue: AttributeNotation
): SingularNotationEvent()


data class UpdatedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeNesting: AttributePath,
        val attributeNotation: AttributeNotation
): SingularNotationEvent()


data class RemovedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributePath: AttributePath
): SingularNotationEvent()


//--------------------------------------------------------------
sealed class InsertedInAttributeEvent: SingularNotationEvent()


data class InsertedListItemInAttributeEvent(
        val objectLocation: ObjectLocation,
        val containingList: AttributePath,
        val indexInList: PositionIndex,
        val item: AttributeNotation
): InsertedInAttributeEvent()


data class InsertedMapEntryInAttributeEvent(
        val objectLocation: ObjectLocation,
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
)


data class InsertedObjectListItemInAttributeEvent(
        val addedObject: AddedObjectEvent,
        val insertedInAttribute: InsertedListItemInAttributeEvent
): CompoundNotationEvent(
        listOf(addedObject, insertedInAttribute)
)


//---------------------------------------------------------------------------------------------------------------------
data class RenameRefactoredEvent(
        val renamedObject: RenamedObjectEvent,
        val adjustedReferences: List<UpdatedInAttributeEvent>
): CompoundNotationEvent(
        listOf(renamedObject).plus(adjustedReferences)
)


data class RenameDocumentRefactoredEvent(
        val createdWithNewName: CopiedDocumentEvent,
        val removedUnderOldName: DeletedDocumentEvent
): CompoundNotationEvent(
        listOf(createdWithNewName, removedUnderOldName)
)