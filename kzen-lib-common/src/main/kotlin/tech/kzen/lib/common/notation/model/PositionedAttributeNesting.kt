package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.AttributePath


data class PositionedAttributeNesting(
        val attributeNesting: AttributePath,
        val positionIndex: PositionIndex
)