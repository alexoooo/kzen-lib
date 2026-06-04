package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ServiceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.platform.collect.toPersistentMap


@Reflect
object AttributeObjectCreator: ObjectCreator {
    private val defaultParameterCreator = ObjectReference.parse(
            DefinitionAttributeCreator::class.simpleName!!)

    private val serviceParameterCreator = ObjectReference.parse(
            ServiceAttributeCreator::class.simpleName!!)

    override fun create(
        objectLocation: ObjectLocation,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance,
        environment: GraphEnvironment
    ): ObjectInstance {
        val objectMetadata = graphStructure.graphMetadata.get(objectLocation)
            ?: throw IllegalArgumentException("Missing metadata: $objectLocation")

        val constructorArguments = mutableListOf<Any?>()

        val constructorArgumentNames =
            GlobalMirror.constructorArgumentNames(objectDefinition.className)

        val constructorInstances = mutableMapOf<AttributeName, Any?>()

        for (argumentName in constructorArgumentNames) {
            val argumentAttribute = AttributeName(argumentName)

            // A @Service parameter is resolved from the environment, not notation, so it has no
            // attribute metadata and routes to the ServiceAttributeCreator by its definition type.
            val attributeCreatorReference =
                if (objectDefinition.attributeDefinitions.map[argumentAttribute] is ServiceAttributeDefinition) {
                    serviceParameterCreator
                }
                else {
                    objectMetadata.attributes.map[argumentAttribute]
                            ?.creatorReference
                            ?: defaultParameterCreator
                }

            val attributeCreatorLocation = partialGraphInstance.objectInstances.locate(
                    attributeCreatorReference)

            val attributeCreator = partialGraphInstance
                    .objectInstances[attributeCreatorLocation]?.reference as AttributeCreator

            val attributeInstance = attributeCreator.create(
                    objectLocation, argumentAttribute, graphStructure, objectDefinition, partialGraphInstance,
                    environment)

            constructorArguments.add(attributeInstance)

            constructorInstances[argumentAttribute] = attributeInstance
        }

        val instance = GlobalMirror.create(objectDefinition.className, constructorArguments)

        return ObjectInstance(
                instance,
                AttributeNameMap(constructorInstances.toPersistentMap()))
    }
}