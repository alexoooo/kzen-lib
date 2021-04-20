package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
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
    fun references(): Set<ObjectReference> {
        val builder = mutableSetOf<ObjectReference>()

        val attributeReferences = attributeReferences()
        builder.addAll(attributeReferences.values)

        builder.add(creator)
        builder.addAll(creatorDependencies)

        return builder
    }


    fun attributeReferences(): Map<AttributePath, ObjectReference> {
        return attributeReferences(false)
    }

    
    fun attributeReferencesIncludingWeak(): Map<AttributePath, ObjectReference> {
        return attributeReferences(true)
    }

    
    private fun attributeReferences(
        includeWeak: Boolean
    ): Map<AttributePath, ObjectReference> {
        val builder = mutableMapOf<AttributePath, ObjectReference>()

        for (e in attributeDefinitions.values) {
            val attributeReferences = attributeReferences(e.value, includeWeak)

            for (attributeReference in attributeReferences) {
                val path = AttributePath(e.key, attributeReference.key)
                builder[path] = attributeReference.value
            }
        }

        return builder
    }
    
    
    private fun attributeReferences(
        definition: AttributeDefinition,
        includeWeak: Boolean
    ): Map<AttributeNesting, ObjectReference> {
        val builder = mutableMapOf<AttributeNesting, ObjectReference>()

        traverseAttribute(
                AttributeNesting.empty,
                definition,
                builder,
                includeWeak
        )

        return builder
    }


    private fun traverseAttribute(
        nesting: AttributeNesting,
        definition: AttributeDefinition,
        builder: MutableMap<AttributeNesting, ObjectReference>,
        includeWeak: Boolean
    ) {
        when (definition) {
            is ReferenceAttributeDefinition -> {
                if (definition.objectReference == null ||
                        definition.weak && ! includeWeak) {
                    return
                }

                builder[nesting] = definition.objectReference
            }

            is ListAttributeDefinition -> {
                for ((index, value) in definition.values.withIndex()) {
                    val segment = AttributeSegment.ofIndex(index)
                    val indexNesting = nesting.push(segment)
                    traverseAttribute(indexNesting, value, builder, includeWeak)
                }
            }

            is MapAttributeDefinition -> {
                for ((key, value) in definition.values) {
                    val segment = AttributeSegment.ofKey(key)
                    val keyNesting = nesting.push(segment)
                    traverseAttribute(keyNesting, value, builder, includeWeak)
                }
            }
        }
    }
}