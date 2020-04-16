package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


@Suppress("unused")
class WeakAttributeDefiner(
//        private val reference: Boolean
): AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
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
                ?: return AttributeDefinitionAttempt.failure(
                        "Unknown attribute: $objectLocation - $attributeName")

        return when (attributeNotation) {
            is ScalarAttributeNotation ->
                defineScalar(objectLocation, attributeName, /*graphStructure.graphNotation,*/ attributeNotation)

            is ListAttributeNotation ->
                defineList(objectLocation, attributeName, /*graphStructure,*/ attributeNotation)

            else ->
                TODO("ScalarAttributeNotation expected: $objectLocation - $attributeName - $attributeNotation")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineList(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
//            graphStructure: GraphStructure,
            attributeNotation: ListAttributeNotation
    ): AttributeDefinitionAttempt {
        val items = mutableListOf<ReferenceAttributeDefinition>()

        for (itemAttributeNotation in attributeNotation.values) {
            if (itemAttributeNotation is ScalarAttributeNotation) {
                val definitionAttempt = defineScalar(
                        objectLocation, attributeName, itemAttributeNotation)

                when (definitionAttempt) {
                    is AttributeDefinitionSuccess -> {
                        val attributeDefinition =
                                definitionAttempt.value as? ReferenceAttributeDefinition
                                ?: TODO("ValueAttributeDefinition expected: ${definitionAttempt.value} - " +
                                        "$objectLocation - $attributeName - $attributeNotation")

                        items.add(attributeDefinition)
                    }

                    is AttributeDefinitionFailure -> {
                        return definitionAttempt
                    }
                }
            }
            else {
                TODO("List of ScalarAttributeNotation expected: " +
                        "$objectLocation - $attributeName - $attributeNotation")
            }
        }

        return AttributeDefinitionAttempt.success(
                ListAttributeDefinition(items))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineScalar(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            scalarAttributeNotation: ScalarAttributeNotation
    ): AttributeDefinitionAttempt {
        val objectReference = scalarAttributeNotation.asString()?.let { ObjectReference.parse(it) }
                ?: return AttributeDefinitionAttempt.failure(
                        "Reference expected: $objectLocation - $attributeName")

//        val value: ObjectReference =
//                if (reference) {
//                    objectReference
//                }
//                else {
//                    graphNotation.coalesce.locate(
//                            objectLocation, objectReference
//                    ).toReference()
//                }

        return AttributeDefinitionAttempt.success(
                ReferenceAttributeDefinition(
//                        value,
                        objectReference,
                        weak = true))
    }
}