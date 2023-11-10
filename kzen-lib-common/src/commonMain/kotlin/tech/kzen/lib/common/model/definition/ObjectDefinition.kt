package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.platform.ClassName


// TODO: should creator be ObjectLocation?
data class ObjectDefinition(
    val className: ClassName,
    val attributeDefinitions: AttributeNameMap<AttributeDefinition>,
    val creator: ObjectReference,

    /**
     * Attribute creators or any other objects that are required by the object creator.
     * Combined with every ReferenceAttributeDefinition in attributeDefinitions to
     *  determine creation dependency DAG.
     */
    val creatorDependencies: Set<ObjectReference>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun get(attributePath: AttributePath): AttributeDefinition {
        val root = attributeDefinitions.values[attributePath.attribute]
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
    fun references(): Set<ObjectDefinitionReference> {
        val builder = mutableSetOf<ObjectDefinitionReference>()

        val attributeReferences = attributeReferences()
        builder.addAll(attributeReferences.values)

        builder.add(ObjectDefinitionReference.ofCreatorRelated(creator))
        creatorDependencies.forEach {
            builder.add(ObjectDefinitionReference.ofCreatorRelated(it))
        }

        return builder
    }


    fun attributeReferences(): Map<AttributePath, ObjectDefinitionReference> {
        return attributeReferences(false)
    }

    
    fun attributeReferencesIncludingWeak(): Map<AttributePath, ObjectDefinitionReference> {
        return attributeReferences(true)
    }

    
    private fun attributeReferences(
        includeWeak: Boolean
    ): Map<AttributePath, ObjectDefinitionReference> {
        val builder = mutableMapOf<AttributePath, ObjectDefinitionReference>()

        for (e in attributeDefinitions.values) {
            val attributePaths = attributeReferences(e.key, e.value, includeWeak)

            for (attributeReference in attributePaths) {
                builder[attributeReference.key] = attributeReference.value
            }
        }

        return builder
    }
    
    
    private fun attributeReferences(
        attributeName: AttributeName,
        definition: AttributeDefinition,
        includeWeak: Boolean
    ): Map<AttributePath, ObjectDefinitionReference> {
        val builder = mutableMapOf<AttributePath, ObjectDefinitionReference>()

        traverseAttribute(
            AttributePath.ofName(attributeName),
            definition,
            builder,
            includeWeak
        )

        return builder
    }


    private fun traverseAttribute(
        attributePath: AttributePath,
        definition: AttributeDefinition,
        builder: MutableMap<AttributePath, ObjectDefinitionReference>,
        includeWeak: Boolean
    ) {
        when (definition) {
            is ReferenceAttributeDefinition -> {
                if (definition.objectReference == null ||
                        definition.weak && ! includeWeak) {
                    return
                }

                builder[attributePath] = ObjectDefinitionReference.ofAttribute(
                    definition.objectReference, attributePath)
            }

            is ListAttributeDefinition -> {
                for ((index, value) in definition.values.withIndex()) {
                    val segment = AttributeSegment.ofIndex(index)
//                    val indexNesting = attributePath.push(segment)
                    val indexNesting = attributePath.nest(segment)
                    traverseAttribute(indexNesting, value, builder, includeWeak)
                }
            }

            is MapAttributeDefinition -> {
                for ((key, value) in definition.values) {
                    val segment = AttributeSegment.ofKey(key)
//                    val keyNesting = attributePath.push(segment)
                    val keyNesting = attributePath.nest(segment)
                    traverseAttribute(keyNesting, value, builder, includeWeak)
                }
            }

            is ValueAttributeDefinition -> {}
        }
    }
}