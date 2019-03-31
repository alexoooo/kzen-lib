package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.Mirror


@Suppress("unused")
class AttributeObjectCreator: ObjectCreator {
    companion object {
        private val defaultParameterCreator = ObjectReference.parse(
                DefinitionAttributeCreator::class.simpleName!!)
    }

    override fun create(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any {
        val objectMetadata = graphStructure.graphMetadata.get(objectLocation)

        val constructorArguments = mutableListOf<Any?>()

        val constructorArgumentNames =
                Mirror.constructorArgumentNames(objectDefinition.className)

        for (argumentName in constructorArgumentNames) {
            val argumentAttribute = AttributeName(argumentName)

            val attributeMetadata = objectMetadata.attributes[argumentAttribute]
//                    ?: throw IllegalArgumentException("Attribute metadata not found: $argumentAttribute")

            val attributeCreatorReference = attributeMetadata
                    ?.creatorReference
                    ?: defaultParameterCreator

            val attributeCreatorLocation = partialGraphInstance.objects.locate(
                    objectLocation, attributeCreatorReference)

            val attributeCreator = partialGraphInstance
                    .objects.get(attributeCreatorLocation) as AttributeCreator

            val attributeInstance = attributeCreator.create(
                    objectLocation, argumentAttribute, graphStructure, objectDefinition, partialGraphInstance)

            constructorArguments.add(attributeInstance)
        }

        return Mirror.create(objectDefinition.className, constructorArguments)
    }
}