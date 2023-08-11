package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.attribute.AttributePath


data class AttributeLocation(
    val attributePath: AttributePath,
    val objectLocation: ObjectLocation
)