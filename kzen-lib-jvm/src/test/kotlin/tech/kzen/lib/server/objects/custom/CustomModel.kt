package tech.kzen.lib.server.objects.custom

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.AttributeDefinitionSuccess
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


data class CustomModel(
    val value: String
) {
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            val attributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, attributeName)
//                ?: return AttributeDefinitionAttempt.failure(
//                    "'${attributeName}' attribute notation not found:" +
//                            " $objectLocation - $attributeName")

            return AttributeDefinitionSuccess(
                ValueAttributeDefinition(
                    CustomModel(attributeNotation.asString()!!)
                ))
        }
    }
}