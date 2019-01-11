package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.ObjectReference


sealed class AttributeDefinition {
    abstract fun references(): Set<ObjectReference>
}


data class ValueAttributeDefinition(
        val value: Any?
): AttributeDefinition() {
    override fun references(): Set<ObjectReference> = setOf()
}


data class ReferenceAttributeDefinition(
        val objectReference: ObjectReference?
): AttributeDefinition() {
    override fun references(): Set<ObjectReference> =
            if (objectReference == null) {
                setOf()
            } else {
                setOf(objectReference)
            }
}


// TODO: should this be CollectionParameterDefinition or something else that's more generic?
data class ListAttributeDefinition(
        val values: List<AttributeDefinition>
): AttributeDefinition() {
    override fun references(): Set<ObjectReference> {
        val builder = mutableSetOf<ObjectReference>()
        for (value in values) {
            builder.addAll(value.references())
        }
        return builder
    }
}


data class MapAttributeDefinition(
        val values: Map<String, AttributeDefinition>
): AttributeDefinition() {
    override fun references(): Set<ObjectReference> {
        val builder = mutableSetOf<ObjectReference>()
        for (value in values.values) {
            builder.addAll(value.references())
        }
        return builder
    }
}
