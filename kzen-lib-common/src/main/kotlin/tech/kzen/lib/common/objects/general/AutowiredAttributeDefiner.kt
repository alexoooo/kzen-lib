package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class AutowiredAttributeDefiner(
        private val weak: Boolean
): AttributeDefiner {
    companion object {
        val findSegment = AttributeSegment.ofKey("find")
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinition {
        val attributeMetadata = graphStructure.graphMetadata.get(objectLocation).attributes[attributeName]
                ?: throw IllegalArgumentException("Metadata not found: $objectLocation - $attributeName")

        val findIs = ObjectReference.parse(findIs(attributeMetadata))
        val findIsLocation = graphStructure.graphNotation.coalesce.locate(objectLocation, findIs)

        val references = mutableListOf<AttributeDefinition>()

        for ((location, notation) in graphStructure.graphNotation.coalesce.values) {
            val isReference = notation.attributes[NotationConventions.isAttributeName]?.asString()
                    ?: continue

            val isLocation = graphStructure.graphNotation.coalesce
                    .locate(location, ObjectReference.parse(isReference))
            if (findIsLocation != isLocation) {
                continue
            }

            val attributeDefinition = defineLocation(location)

            references.add(attributeDefinition)
        }

        return ListAttributeDefinition(references)
    }


    private fun findIs(attributeMetadata: AttributeMetadata): String {
        val find = attributeMetadata.attributeMetadataNotation.values[findSegment]
                ?: return attributeOf(attributeMetadata)

        if (find is MapAttributeNotation) {
            return find.get(NotationConventions.isAttributeSegment)?.asString()
                    ?: attributeOf(attributeMetadata)
        }

        if (find is ScalarAttributeNotation) {
            return find.value
        }

        throw UnsupportedOperationException("Can't find autowire type: $attributeMetadata")
    }


    private fun attributeOf(attributeMetadata: AttributeMetadata): String {
        return (attributeMetadata.attributeMetadataNotation.values[NotationConventions.ofAttributeSegment]
                as? ScalarAttributeNotation
                )?.value
                ?: throw UnsupportedOperationException("Can't find autowire type: $attributeMetadata")
    }


    private fun defineLocation(objectLocation: ObjectLocation): AttributeDefinition {
        return if (weak) {
            ValueAttributeDefinition(objectLocation)
        }
        else {
            ReferenceAttributeDefinition(objectLocation.toReference())
        }
    }
}