package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.context.definition.AttributeDefinition
import tech.kzen.lib.common.context.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class WeakAttributeDefiner(
        private val reference: Boolean
): AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]!!

        val attributeNotation = objectNotation.attributes.values[attributeName]
                ?: graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, attributeName.asAttributeNesting())
                ?: AttributeDefinitionAttempt.failure("Unknown attribute: $objectLocation - $attributeName")

        if (attributeNotation is ScalarAttributeNotation) {
            return define(objectLocation, attributeName, graphStructure.graphNotation, attributeNotation)
        }
        else {
            TODO()
        }
//        else if (attributeNotation is ListAttributeNotation) {
//            val items = mutableListOf<ValueAttributeDefinition>()
//            for (item in attributeNotation.values) {
//
//            }
//        }
    }


    private fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphNotation: GraphNotation,
            scalarAttributeNotation: ScalarAttributeNotation
    ): AttributeDefinitionAttempt {
        val objectReference = scalarAttributeNotation.asString()?.let { ObjectReference.parse(it) }
                ?: return AttributeDefinitionAttempt.failure(
                        "Reference expected: $objectLocation - $attributeName")

        val value: Any? =
                if (reference) {
                    objectReference
                }
                else {
                    graphNotation.coalesce.locate(objectLocation, objectReference)
                }

        return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(value))
    }
}