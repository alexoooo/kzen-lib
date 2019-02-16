package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
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
        val objectNotation = graphStructure.graphNotation.coalesce.get(objectLocation)

        // TODO: is the transitiveParameter here handled correctly? what about default values?
        val parameterNotation = objectNotation.attributes[attributeName]
                ?: graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, attributeName.asAttributeNesting())
                ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")

        val objectMetadata = graphStructure.graphMetadata.objectMetadata.get(objectLocation)
        val parameterMetadata = objectMetadata.attributes[attributeName]!!

        val typeMetadata = parameterMetadata.type!!

        return defineRecursively(parameterNotation, typeMetadata)
    }


    private fun defineRecursively(
            parameterNotation: AttributeNotation,
            typeMetadata: TypeMetadata
    ): AttributeDefinition {
        if (parameterNotation is ScalarAttributeNotation) {
            val className = typeMetadata.className

            if (parameterNotation.value is String && className != ClassNames.kotlinString) {
                return ReferenceAttributeDefinition(
                        ObjectReference.parse(parameterNotation.value))
            }

            if (className == ClassNames.kotlinString && parameterNotation.value !is String) {
                return ValueAttributeDefinition(parameterNotation.value.toString())
            }

            return ValueAttributeDefinition(parameterNotation.value)
        }
        else if (parameterNotation is ListAttributeNotation) {
            val listGeneric = typeMetadata.generics[0]

            val definitions = mutableListOf<AttributeDefinition>()
            for (value in parameterNotation.values) {
                val definition = defineRecursively(value, listGeneric)
                definitions.add(definition)
            }
            return ListAttributeDefinition(definitions)
        }

        TODO()
    }
}