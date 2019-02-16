package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.platform.ClassName


// TODO: is creatorReferences necessary?
// TODO: should creator be ObjectLocation?
data class ObjectDefinition(
        val className: ClassName,
        val attributeDefinitions: Map<AttributeName, AttributeDefinition>,
        val creator: ObjectReference,
        val creatorReferences: Set<ObjectReference>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun get(attributePath: AttributePath): AttributeDefinition {
        val root = attributeDefinitions[attributePath.attribute]
                ?: throw IllegalArgumentException("Missing attribute definition: ${attributePath.attribute}")

        if (attributePath.nesting.segments.isEmpty()) {
            return root
        }

        var cursor: AttributeDefinition = root

        for (attributeSegment in attributePath.nesting.segments) {
            cursor = when (cursor) {
                is ListAttributeDefinition ->
                    cursor.values[attributeSegment.asIndex()!!]

                is MapAttributeDefinition ->
                    cursor.values[attributeSegment.asKey()]
                            ?: throw IllegalArgumentException("Missing key: ${attributeSegment.asKey()}")

                else ->
                    throw IllegalStateException()
            }
        }

        return cursor
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun references(): Set<ObjectReference> {
        val builder = mutableSetOf<ObjectReference>()

        val attributeReferences = attributeReferences()
        builder.addAll(attributeReferences.values)

        builder.add(creator)
        builder.addAll(creatorReferences)

        return builder
    }


    fun attributeReferences(): Map<AttributePath, ObjectReference> {
        val builder = mutableMapOf<AttributePath, ObjectReference>()

        for (e in attributeDefinitions) {
            val attributeReferences = attributeReferences(e.value)

            for (attributeReference in attributeReferences) {
                val path = AttributePath(e.key, attributeReference.key)
                builder[path] = attributeReference.value
            }
        }

        return builder
    }


    private fun attributeReferences(
            definition: AttributeDefinition
    ): Map<AttributeNesting, ObjectReference> {
        val builder = mutableMapOf<AttributeNesting, ObjectReference>()

        traverseAttribute(
                AttributeNesting.empty,
                definition,
                builder
        )

        return builder
    }


    private fun traverseAttribute(
            nesting: AttributeNesting,
            definition: AttributeDefinition,
            builder: MutableMap<AttributeNesting, ObjectReference>
    ) {
        when (definition) {
            is ReferenceAttributeDefinition -> {
                if (definition.objectReference == null) {
                    return
                }

                builder[nesting] = definition.objectReference
            }

            is ListAttributeDefinition -> {
                for ((index, value) in definition.values.withIndex()) {
                    val segment = AttributeSegment.ofIndex(index)
                    val indexNesting = nesting.push(segment)
                    traverseAttribute(indexNesting, value, builder)
                }
            }

            is MapAttributeDefinition -> {
                for ((key, value) in definition.values) {
                    val segment = AttributeSegment.ofKey(key)
                    val keyNesting = nesting.push(segment)
                    traverseAttribute(keyNesting, value, builder)
                }
            }
        }
    }
}