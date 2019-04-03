package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.model.attribute.AttributePath


data class PositionedAttributeNesting(
        val attributePath: AttributePath,
        val positionIndex: PositionIndex
)