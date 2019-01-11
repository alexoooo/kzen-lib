package tech.kzen.lib.common.notation.edit

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.api.model.AttributeNesting
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.notation.model.PositionIndex


//---------------------------------------------------------------------------------------------------------------------
sealed class NotationEvent



//---------------------------------------------------------------------------------------------------------------------
data class BundleCreatedEvent(
        val bundlePath: BundlePath
): NotationEvent()



data class BundleDeletedEvent(
        val bundlePath: BundlePath
): NotationEvent()



//---------------------------------------------------------------------------------------------------------------------
data class ObjectAddedEvent(
        val objectLocation: ObjectLocation,
        val objectNotation: ObjectNotation
): NotationEvent()



data class ObjectRemovedEvent(
        val objectLocation: ObjectLocation
): NotationEvent()



data class ObjectShiftedEvent(
        val objectLocation: ObjectLocation,
        val newPositionInBundle: PositionIndex
): NotationEvent()



data class ObjectRenamedEvent(
        val objectLocation: ObjectLocation,
        val newName: ObjectName
): NotationEvent()


//---------------------------------------------------------------------------------------------------------------------
data class ParameterEditedEvent(
        val objectLocation: ObjectLocation,
        val attributeNesting: AttributeNesting,
        val attributeValue: AttributeNotation
): NotationEvent()
