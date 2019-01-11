package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.AttributeNesting


data class PositionedAttributeNesting(
        val attributeNesting: AttributeNesting,
        val positionIndex: PositionIndex
)