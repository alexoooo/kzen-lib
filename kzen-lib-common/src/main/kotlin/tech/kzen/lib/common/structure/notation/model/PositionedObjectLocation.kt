package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.api.model.ObjectLocation


data class PositionedObjectLocation(
        val objectLocation: ObjectLocation,
        val positionIndex: PositionIndex
)