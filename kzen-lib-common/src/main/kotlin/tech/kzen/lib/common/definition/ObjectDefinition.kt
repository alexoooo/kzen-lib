package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectReference


data class ObjectDefinition(
        val className: String,
        val constructorArguments: Map<AttributeName, AttributeDefinition>,
        val creator: ObjectReference,
        val creatorReferences: Set<ObjectReference>
) {
    fun references(): Set<ObjectReference> {
        val builder = mutableSetOf<ObjectReference>()

        for (e in constructorArguments) {
            builder.addAll(e.value.references())
        }

        builder.add(creator)
        builder.addAll(creatorReferences)

        return builder
    }
}