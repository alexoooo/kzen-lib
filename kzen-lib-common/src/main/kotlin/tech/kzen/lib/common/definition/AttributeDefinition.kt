package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.ObjectReference


sealed class AttributeDefinition


data class ValueAttributeDefinition(
        val value: Any?
): AttributeDefinition()


data class ReferenceAttributeDefinition(
        val objectReference: ObjectReference?
): AttributeDefinition()


data class ListAttributeDefinition(
        val values: List<AttributeDefinition>
): AttributeDefinition()


data class MapAttributeDefinition(
        val values: Map<String, AttributeDefinition>
): AttributeDefinition()