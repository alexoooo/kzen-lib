package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.ParameterCreator
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.platform.Mirror


class ParameterObjectCreator : ObjectCreator {
    companion object {
        private val defaultParameterCreator =
                StructuralParameterCreator::class.simpleName!!
    }

    override fun create(
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            objectGraph: ObjectGraph
    ): Any {
        val constructorArguments = mutableListOf<Any?>()
        for (constructorArg in objectDefinition.constructorArguments) {
            val parameterMetadata = objectMetadata.parameters[constructorArg.key]!!
            val parameterCreatorName = parameterMetadata.creator ?: defaultParameterCreator
            val parameterCreator = objectGraph.get(parameterCreatorName) as ParameterCreator

            val parameterInstance = parameterCreator.create(
                    constructorArg.value, parameterMetadata, objectGraph)

            constructorArguments.add(parameterInstance)
        }

        return Mirror.create(objectDefinition.className, constructorArguments)
    }
}