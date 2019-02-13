package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.platform.Mirror


@Suppress("unused")
class AttributeObjectCreator: ObjectCreator {
    companion object {
        private val defaultParameterCreator =
                StructuralAttributeCreator::class.simpleName!!
    }

    override fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            graphInstance: GraphInstance
    ): Any {
        val constructorArguments = mutableListOf<Any?>()

        val constructorArgumentNames =
                Mirror.constructorArgumentNames(objectDefinition.className)

        for (argumentName in constructorArgumentNames) {
            val argumentAttribute = AttributeName(argumentName)

            val argumentDefinition = objectDefinition.attributeDefinitions[argumentAttribute]
                    ?: throw IllegalArgumentException("Attribute definition not found: $argumentAttribute")

            val attributeMetadata = objectMetadata.attributes[argumentAttribute]
                    ?: throw IllegalArgumentException("Attribute metadata not found: $argumentAttribute")

            val attributeCreatorReference = attributeMetadata.creatorReference ?: defaultParameterCreator
            val attributeCreatorLocation = graphInstance.objects.locate(
                    objectLocation, ObjectReference.parse(attributeCreatorReference))

            val attributeCreator = graphInstance.objects.get(attributeCreatorLocation) as AttributeCreator


            val attributeInstance = attributeCreator.create(
                    objectLocation, argumentDefinition, attributeMetadata, graphInstance)

            constructorArguments.add(attributeInstance)
        }

        return Mirror.create(objectDefinition.className, constructorArguments)
    }
}