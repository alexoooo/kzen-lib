package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.attribute.AttributeNameMap


data class ObjectInstance(
        val reference: Any,
        val constructorAttributes: AttributeNameMap<Any?>
)