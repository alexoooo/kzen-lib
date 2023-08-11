package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


// TODO: consider convention of TypeName$Creator?
@Reflect
object DefinitionAttributeCreator: AttributeCreator {
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance
    ): Any? {
        val attributeDefinition = objectDefinition.attributeDefinitions.values[attributeName]
            ?: throw IllegalArgumentException(
                "Attribute definition missing: $objectLocation - $attributeName - $objectDefinition")

        return createDefinition(
                objectLocation, attributeDefinition, partialGraphInstance, graphStructure, attributeName)
    }


    private fun createDefinition(
        objectLocation: ObjectLocation,
        attributeDefinition: AttributeDefinition,
        partialGraphInstance: GraphInstance,
        graphStructure: GraphStructure,
        attributeName: AttributeName
    ): Any? {
        return when (attributeDefinition) {
            is ValueAttributeDefinition -> {
                attributeDefinition.value
            }

            is ReferenceAttributeDefinition -> {
                val objectReference = attributeDefinition.objectReference!!

                if (objectReference.isEmpty()) {
                    null
                }
                else if (attributeDefinition.weak) {
                    graphStructure.graphNotation.coalesce.locateOptional(
                        objectReference, ObjectReferenceHost.ofLocation(objectLocation)
                    ) ?: throw IllegalArgumentException(
                        "Missing $objectReference in $objectLocation for $attributeName - $attributeDefinition")
                }
                else {
                    val location = partialGraphInstance.objectInstances.locate(
                            objectReference, ObjectReferenceHost.ofLocation(objectLocation))
                    partialGraphInstance[location]?.reference
                }
            }

            is ListAttributeDefinition ->
                attributeDefinition.values.map {
                    createDefinition(objectLocation, it, partialGraphInstance, graphStructure, attributeName)
                }

            else ->
                TODO("Not supported (yet): $attributeDefinition")
        }
    }
}