package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.api.model.ObjectPath


data class PositionedObjectPath(
        val objectPath: ObjectPath,
        val positionIndex: PositionIndex
)