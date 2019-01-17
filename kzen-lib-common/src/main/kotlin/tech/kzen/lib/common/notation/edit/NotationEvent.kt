package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.model.PositionedAttributeNesting


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationEvent


sealed class SingularNotationEvent: NotationEvent()

sealed class CompoundNotationEvent(
        val singularEvents: List<SingularNotationEvent>
): NotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class CreatedBundleEvent(
        val bundlePath: BundlePath
): SingularNotationEvent()



data class DeletedBundleEvent(
        val bundlePath: BundlePath
): SingularNotationEvent()



//---------------------------------------------------------------------------------------------------------------------
data class AddedObjectEvent(
        val objectLocation: ObjectLocation,
        val objectNotation: ObjectNotation
): SingularNotationEvent()



data class RemovedObjectEvent(
        val objectLocation: ObjectLocation
): SingularNotationEvent()



data class ShiftedObjectEvent(
        val objectLocation: ObjectLocation,
        val newPositionInBundle: PositionIndex
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

//data class ParameterEditedEvent(
//        val objectLocation: ObjectLocation,
//        val attributeNesting: AttributeNesting,
//        val attributeValue: AttributeNotation
//): NotationEvent()

data class UpdatedInAttributeEvent(
        val objectLocation: ObjectLocation,
        val attributeNesting: AttributePath,
        val attributeNotation: AttributeNotation
): SingularNotationEvent()


data class InsertedListItemInAttributeEvent(
        val objectLocation: ObjectLocation,
        val containingList: PositionedAttributeNesting,
        val item: AttributeNotation
): SingularNotationEvent()


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