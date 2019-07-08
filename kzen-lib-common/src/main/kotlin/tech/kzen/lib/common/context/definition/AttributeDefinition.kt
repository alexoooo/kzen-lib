package tech.kzen.lib.common.context.definition

import tech.kzen.lib.common.model.locate.ObjectReference


sealed class AttributeDefinition


data class ValueAttributeDefinition(
        val value: Any?
): AttributeDefinition()


data class ReferenceAttributeDefinition(
        val objectReference: ObjectReference?,
        val weak: Boolean = false
): AttributeDefinition()


data class ListAttributeDefinition(
        val values: List<AttributeDefinition>
): AttributeDefinition()


data class MapAttributeDefinition(
        val values: Map<String, AttributeDefinition>
): AttributeDefinition()