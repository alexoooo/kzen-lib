package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.TypeMetadata
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.notation.model.ListAttributeNotation
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassNames


class NotationAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            projectNotation: GraphNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: GraphInstance
    ): AttributeDefinition {
        val objectNotation = projectNotation.coalesce.get(objectLocation)

        // TODO: is the transitiveParameter here handled correctly? what about default values?
        val parameterNotation = objectNotation.attributes[attributeName]
                ?: projectNotation.transitiveAttribute(objectLocation, attributeName.asAttributeNesting())
                ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")

        val objectMetadata = projectMetadata.objectMetadata.get(objectLocation)
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