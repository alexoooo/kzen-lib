package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class WeakLiteralAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinition {
        val objectNotation = graphStructure.graphNotation.coalesce.get(objectLocation)

        val attributeNotation = objectNotation.attributes[attributeName]
                ?: graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, attributeName.asAttributeNesting())
                ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")

        if (attributeNotation is ScalarAttributeNotation) {
            return define(objectLocation, attributeName, partialGraphDefinition, attributeNotation)
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
            partialGraphDefinition: GraphDefinition,
            scalarAttributeNotation: ScalarAttributeNotation
    ): ValueAttributeDefinition {
        val objectReference = scalarAttributeNotation.asString()?.let { ObjectReference.parse(it) }
                ?: throw IllegalArgumentException("Reference expected: $objectLocation - $attributeName")

        val dependencyLocation = partialGraphDefinition
                .objectDefinitions.locate(objectLocation, objectReference)

        return ValueAttributeDefinition(dependencyLocation)
    }
}