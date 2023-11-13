package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment


data class AttributeLocation(
    val objectLocation: ObjectLocation,
    val attributePath: AttributePath
) {
    fun nest(attributeSegment: AttributeSegment): AttributeLocation {
        return AttributeLocation(
            objectLocation,
            attributePath.nest(attributeSegment))
    }


    fun nest(attributeNesting: AttributeNesting): AttributeLocation {
        return AttributeLocation(
            objectLocation,
            attributePath.nest(attributeNesting))
    }
}