package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.context.definition.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.TypeMetadata
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassNames


class StructuralAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinition {
        val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]
                ?: throw IllegalArgumentException("Unknown object notation: $objectLocation")

        val attributeNotation = objectNotation.attributes.values[attributeName]
                ?: graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, attributeName.asAttributeNesting())
                ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")

        val objectMetadata = graphStructure.graphMetadata.objectMetadata[objectLocation]
                ?: throw IllegalArgumentException("Unknown object metadata: $objectLocation")

        val attributeMetadata = objectMetadata.attributes.values[attributeName]
//                ?: inferMetadata(objectLocation, attributeName, graphStructure.graphNotation)

        val typeMetadata = attributeMetadata?.type
                ?: TypeMetadata.any

        return defineRecursively(attributeNotation, typeMetadata)
    }


    private fun defineRecursively(
            attributeNotation: AttributeNotation,
            typeMetadata: TypeMetadata
    ): AttributeDefinition {
        if (attributeNotation is ScalarAttributeNotation) {
            val className = typeMetadata.className

            if (className == ClassNames.kotlinString) {
                return ValueAttributeDefinition(attributeNotation.value)
            }

            if (className == ClassNames.kotlinBoolean) {
                if (attributeNotation.value == "true") {
                    return ValueAttributeDefinition(true)
                }
                else if (attributeNotation.value == "false") {
                    return ValueAttributeDefinition(false)
                }
                throw IllegalArgumentException("Boolean expected: $attributeNotation")
            }

            if (className == ClassNames.kotlinInt) {
                return ValueAttributeDefinition(attributeNotation.value.toInt())
            }

            if (className == ClassNames.kotlinDouble) {
                return ValueAttributeDefinition(attributeNotation.value.toDouble())
            }

            return ReferenceAttributeDefinition(
                    ObjectReference.parse(attributeNotation.value))
        }
        else if (attributeNotation is ListAttributeNotation) {
            val listGeneric = typeMetadata.generics[0]

            val definitions = mutableListOf<AttributeDefinition>()
            for (value in attributeNotation.values) {
                val definition = defineRecursively(value, listGeneric)
                definitions.add(definition)
            }
            return ListAttributeDefinition(definitions)
        }

        TODO()
    }
}