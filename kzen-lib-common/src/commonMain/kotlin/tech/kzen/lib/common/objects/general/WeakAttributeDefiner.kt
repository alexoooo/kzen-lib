package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
object WeakAttributeDefiner/*(
        private val reference: Boolean
)*/: AttributeDefiner {
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
            ?: graphStructure.graphNotation.firstAttribute(
                objectLocation, attributeName.asAttributePath())
            ?: return AttributeDefinitionAttempt.failure(
                "Unknown attribute: $objectLocation - $attributeName")

        val attributeNullable = graphStructure
            .graphMetadata
            .objectMetadata[objectLocation]
            ?.attributes
            ?.get(attributeName)
            ?.type
            ?.nullable
            ?: false

        return when (attributeNotation) {
            is ScalarAttributeNotation ->
                defineScalar(attributeNotation, attributeNullable)

            is ListAttributeNotation ->
                defineList(objectLocation, attributeName, attributeNotation)

            else ->
                TODO("ScalarAttributeNotation expected: $objectLocation - $attributeName - $attributeNotation")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineList(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        attributeNotation: ListAttributeNotation
    ): AttributeDefinitionAttempt {
        val items = mutableListOf<ReferenceAttributeDefinition>()

        for (itemAttributeNotation in attributeNotation.values) {
            if (itemAttributeNotation is ScalarAttributeNotation) {
                val definitionAttempt = defineScalar(
                    itemAttributeNotation, false)

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
        scalarAttributeNotation: ScalarAttributeNotation,
        attributeNullable: Boolean
    ): AttributeDefinitionAttempt {
        val objectReference = scalarAttributeNotation.asString().let { ObjectReference.parse(it) }

        if (objectReference.isEmpty()) {
            if (attributeNullable) {
                return AttributeDefinitionAttempt.success(
                    ReferenceAttributeDefinition(
                        null,
                        weak = true,
                        nullable = attributeNullable))
            }

            return AttributeDefinitionAttempt.failure("Empty object reference")
        }

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
                    objectReference,
                    weak = true,
                    nullable = attributeNullable))
    }
}