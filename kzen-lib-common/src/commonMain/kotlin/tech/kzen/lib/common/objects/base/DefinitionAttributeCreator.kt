package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DefinitionAttributeCreator: AttributeCreator {
    override fun create(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any? {
        val attributeDefinition = objectDefinition.attributeDefinitions.values[attributeName]
                ?: throw IllegalArgumentException("Attribute definition missing: $objectLocation - $attributeName")

        return createDefinition(
                objectLocation, attributeDefinition, partialGraphInstance, graphStructure)
    }


    private fun createDefinition(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            partialGraphInstance: GraphInstance,
            graphStructure: GraphStructure
    ): Any? {
        return when (attributeDefinition) {
            is ValueAttributeDefinition -> {
                attributeDefinition.value
            }

            is ReferenceAttributeDefinition -> {
                val objectReference = attributeDefinition.objectReference!!
                
                if (attributeDefinition.weak) {
                    graphStructure.graphNotation.coalesce.locate(
                            objectReference, ObjectReferenceHost.ofLocation(objectLocation))
                }
                else {
                    val location = partialGraphInstance.objectInstances.locate(
                            objectReference, ObjectReferenceHost.ofLocation(objectLocation))

                    partialGraphInstance[location]?.reference
                }
            }

            is ListAttributeDefinition ->
                attributeDefinition.values.map {
                    createDefinition(objectLocation, it, partialGraphInstance, graphStructure)
                }

            else ->
                TODO("Not supported (yet): $attributeDefinition")
        }
    }
}