package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.structure.GraphStructure


class DefinitionAttributeCreator: AttributeCreator {
    override fun create(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any? {
        val attributeDefinition = objectDefinition.attributeDefinitions[attributeName]!!
        return createDefinition(
                objectLocation, attributeDefinition, partialGraphInstance)
    }


    private fun createDefinition(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            partialGraphInstance: GraphInstance
    ): Any? {
        return when (attributeDefinition) {
            is ValueAttributeDefinition -> {
//                if (parameterMetadata.type?.className == ClassNames.kotlinString) {
//                    parameterDefinition.value.toString()
//                }
//                else {
//                    parameterDefinition.value
//                }
                attributeDefinition.value
            }

            is ReferenceAttributeDefinition -> {
                val location = partialGraphInstance.objects.locate(
                        objectLocation, attributeDefinition.objectReference!!)
                partialGraphInstance.objects.get(location)
            }


            is ListAttributeDefinition ->
                attributeDefinition.values.map {
                    createDefinition(objectLocation, it, partialGraphInstance)
                }

            else ->
                TODO("Not supported (yet): $attributeDefinition")
        }
    }
}