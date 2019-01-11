package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ParameterCreator
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.metadata.model.AttributeMetadata
import tech.kzen.lib.common.api.model.ObjectLocation


class StructuralParameterCreator: ParameterCreator {
    override fun create(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            parameterMetadata: AttributeMetadata,
            objectGraph: ObjectGraph
    ): Any? {
        return createDefinition(
                objectLocation, attributeDefinition, /*parameterMetadata,*/ objectGraph)
    }


    private fun createDefinition(
            objectLocation: ObjectLocation,
            parameterDefinition: AttributeDefinition,
//            parameterMetadata: ParameterMetadata,
            objectGraph: ObjectGraph
    ): Any? {
        return when (parameterDefinition) {
            is ValueAttributeDefinition -> {
//                if (parameterMetadata.type?.className == ClassNames.kotlinString) {
//                    parameterDefinition.value.toString()
//                }
//                else {
//                    parameterDefinition.value
//                }
                parameterDefinition.value
            }

            is ReferenceAttributeDefinition -> {
                val location = objectGraph.objects.locate(
                        objectLocation, parameterDefinition.objectReference!!)
                objectGraph.objects.get(location)
            }


            is ListAttributeDefinition ->
                parameterDefinition.values.map {
                    createDefinition(objectLocation, it, objectGraph)
                }

            else ->
                TODO("Not supported (yet): $parameterDefinition")
        }
    }
}