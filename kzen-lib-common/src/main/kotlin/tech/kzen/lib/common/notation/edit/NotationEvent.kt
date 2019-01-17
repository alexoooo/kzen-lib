package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.PositionIndex


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationEvent


sealed class SingularNotationEvent: NotationEvent()

sealed class CompoundNotationEvent(
        val directEvents: List<SingularNotationEvent>
): NotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class BundleCreatedEvent(
        val bundlePath: BundlePath
): SingularNotationEvent()



data class BundleDeletedEvent(
        val bundlePath: BundlePath
): SingularNotationEvent()



//---------------------------------------------------------------------------------------------------------------------
data class ObjectAddedEvent(
        val objectLocation: ObjectLocation,
        val objectNotation: ObjectNotation
): SingularNotationEvent()



data class ObjectRemovedEvent(
        val objectLocation: ObjectLocation
): SingularNotationEvent()



data class ObjectShiftedEvent(
        val objectLocation: ObjectLocation,
        val newPositionInBundle: PositionIndex
): SingularNotationEvent()



data class ObjectRenamedEvent(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): SingularNotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class AttributeUpsertedEvent(
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


//---------------------------------------------------------------------------------------------------------------------
data class RenameRefactoredEvent(
        val renamedObject: ObjectRenamedEvent,
        val adjustedReferences: List<UpdatedInAttributeEvent>
): CompoundNotationEvent(
        listOf(renamedObject).plus(adjustedReferences)
)