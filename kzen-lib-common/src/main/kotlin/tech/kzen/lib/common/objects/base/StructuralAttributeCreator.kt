package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.metadata.model.AttributeMetadata
import tech.kzen.lib.common.api.model.ObjectLocation


class StructuralAttributeCreator: AttributeCreator {
    override fun create(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            parameterMetadata: AttributeMetadata,
            objectGraph: GraphInstance
    ): Any? {
        return createDefinition(
                objectLocation, attributeDefinition, /*parameterMetadata,*/ objectGraph)
    }


    private fun createDefinition(
            objectLocation: ObjectLocation,
            parameterDefinition: AttributeDefinition,
//            parameterMetadata: ParameterMetadata,
            objectGraph: GraphInstance
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