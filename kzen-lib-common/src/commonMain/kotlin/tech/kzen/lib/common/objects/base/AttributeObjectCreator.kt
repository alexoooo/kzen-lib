package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.collect.toPersistentMap


@Reflect
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
    ): ObjectInstance {
        val objectMetadata = graphStructure.graphMetadata.get(objectLocation)
                ?: throw IllegalArgumentException("Missing metadata: $objectLocation")

        val constructorArguments = mutableListOf<Any?>()

        val constructorArgumentNames =
            GlobalMirror.constructorArgumentNames(objectDefinition.className)

        val constructorInstances = mutableMapOf<AttributeName, Any?>()

        for (argumentName in constructorArgumentNames) {
            val argumentAttribute = AttributeName(argumentName)

            val attributeMetadata = objectMetadata.attributes.values[argumentAttribute]
//                    ?: throw IllegalArgumentException("Attribute metadata not found: $argumentAttribute")

            val attributeCreatorReference = attributeMetadata
                    ?.creatorReference
                    ?: defaultParameterCreator

            val attributeCreatorLocation = partialGraphInstance.objectInstances.locate(
                    attributeCreatorReference)

            val attributeCreator = partialGraphInstance
                    .objectInstances[attributeCreatorLocation]?.reference as AttributeCreator

            val attributeInstance = attributeCreator.create(
                    objectLocation, argumentAttribute, graphStructure, objectDefinition, partialGraphInstance)

            constructorArguments.add(attributeInstance)

            constructorInstances[argumentAttribute] = attributeInstance
        }

        val instance = GlobalMirror.create(objectDefinition.className, constructorArguments)

        return ObjectInstance(
                instance,
                AttributeNameMap(constructorInstances.toPersistentMap()))
    }
}