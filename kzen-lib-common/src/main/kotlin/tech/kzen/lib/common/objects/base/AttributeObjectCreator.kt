package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.platform.Mirror


class AttributeObjectCreator: ObjectCreator {
    companion object {
        private val defaultParameterCreator =
                StructuralAttributeCreator::class.simpleName!!
    }

    override fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            objectGraph: ObjectGraph
    ): Any {
        val constructorArguments = mutableListOf<Any?>()
        for (constructorArg in objectDefinition.constructorArguments) {
            val parameterMetadata = objectMetadata.attributes[constructorArg.key]!!
            val parameterCreatorReference = parameterMetadata.creator ?: defaultParameterCreator
            val parameterCreatorLocation = objectGraph.objects.locate(
                    objectLocation, ObjectReference.parse(parameterCreatorReference))

            val parameterCreator = objectGraph.objects.get(parameterCreatorLocation) as AttributeCreator

            val parameterInstance = parameterCreator.create(
                    objectLocation, constructorArg.value, parameterMetadata, objectGraph)

            constructorArguments.add(parameterInstance)
        }

        return Mirror.create(objectDefinition.className, constructorArguments)
    }
}