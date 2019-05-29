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
    ): AttributeDefinitionAttempt {
        val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]
                ?: return AttributeDefinitionAttempt.failure("Unknown object notation: $objectLocation")

        val attributeNotation = objectNotation.attributes.values[attributeName]
                ?: graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, attributeName.asAttributeNesting())
                ?: return AttributeDefinitionAttempt.failure("Unknown attribute: $objectLocation - $attributeName")

        val objectMetadata = graphStructure.graphMetadata.objectMetadata[objectLocation]
                ?: return AttributeDefinitionAttempt.failure("Unknown object metadata: $objectLocation")

        val attributeMetadata = objectMetadata.attributes.values[attributeName]
//                ?: inferMetadata(objectLocation, attributeName, graphStructure.graphNotation)

        val typeMetadata = attributeMetadata?.type
                ?: TypeMetadata.any

        return defineRecursively(attributeNotation, typeMetadata)
    }


    private fun defineRecursively(
            attributeNotation: AttributeNotation,
            typeMetadata: TypeMetadata
    ): AttributeDefinitionAttempt {
        if (attributeNotation is ScalarAttributeNotation) {
            val className = typeMetadata.className

            if (className == ClassNames.kotlinString) {
                return AttributeDefinitionAttempt.success(
                        ValueAttributeDefinition(attributeNotation.value))
            }

            if (className == ClassNames.kotlinBoolean) {
                if (attributeNotation.value == "true") {
                    return AttributeDefinitionAttempt.success(
                            ValueAttributeDefinition(true))
                }
                else if (attributeNotation.value == "false") {
                    return AttributeDefinitionAttempt.success(
                            ValueAttributeDefinition(false))
                }
                return AttributeDefinitionAttempt.failure(
                        "Boolean expected: $attributeNotation")
            }

            if (className == ClassNames.kotlinInt) {
                return AttributeDefinitionAttempt.success(
                        ValueAttributeDefinition(attributeNotation.value.toInt()))
            }

            if (className == ClassNames.kotlinDouble) {
                return AttributeDefinitionAttempt.success(
                        ValueAttributeDefinition(attributeNotation.value.toDouble()))
            }

            return AttributeDefinitionAttempt.success(
                    ReferenceAttributeDefinition(
                            ObjectReference.parse(attributeNotation.value)))
        }
        else if (attributeNotation is ListAttributeNotation) {
            val listGeneric = typeMetadata.generics[0]

            val definitions = mutableListOf<AttributeDefinition>()
            for (value in attributeNotation.values) {
                val definitionAttempt = defineRecursively(value, listGeneric)
                val definition = definitionAttempt.value
                        ?: return definitionAttempt
                definitions.add(definition)
            }
            return AttributeDefinitionAttempt.success(
                    ListAttributeDefinition(definitions))
        }

        TODO()
    }
}