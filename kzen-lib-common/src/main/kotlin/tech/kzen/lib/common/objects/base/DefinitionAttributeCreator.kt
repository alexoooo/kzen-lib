package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.context.definition.*
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class DefinitionAttributeCreator: AttributeCreator {
//    companion object {
//        private val defaultDefiner = StructuralAttributeDefiner()
//    }


    override fun create(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any? {
        val attributeDefinition = objectDefinition.attributeDefinitions.values[attributeName]
//                ?: inferDefinition(objectLocation, attributeName, graphStructure)
                ?: throw IllegalArgumentException("Attribute definition missing: $objectLocation - $attributeName")

        return createDefinition(
                objectLocation, attributeDefinition, partialGraphInstance)
    }


//    private fun inferDefinition(
//            objectLocation: ObjectLocation,
//            attributeName: AttributeName,
//            graphStructure: GraphStructure
//    ): AttributeDefinition {
//        return defaultDefiner.define(
//                objectLocation, attributeName, graphStructure)
//    }


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
                partialGraphInstance[location]?.reference
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