package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.platform.ClassName


sealed class AttributeDefinition


data class ValueAttributeDefinition(
    val value: Any?
): AttributeDefinition()


/**
 * A constructor parameter annotated [tech.kzen.lib.common.reflect.Service]: its value is resolved
 * from the [tech.kzen.lib.common.service.context.environment.GraphEnvironment] by [serviceClassName] at creation
 * time, not from notation. Carries no graph reference, so it adds no construction-order dependency.
 */
data class ServiceAttributeDefinition(
    val serviceClassName: ClassName
): AttributeDefinition()


data class ReferenceAttributeDefinition(
    val objectReference: ObjectReference?,
    val weak: Boolean,
    val nullable: Boolean
): AttributeDefinition()


data class ListAttributeDefinition(
    val values: List<AttributeDefinition>
): AttributeDefinition()


data class MapAttributeDefinition(
    val map: Map<String, AttributeDefinition>
): AttributeDefinition() {
    operator fun get(key: String): AttributeDefinition? {
        return map[key]
    }
}