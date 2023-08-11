package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.location.ObjectLocation


data class PositionedObjectLocation(
        val objectLocation: ObjectLocation,
        val positionIndex: PositionIndex
)