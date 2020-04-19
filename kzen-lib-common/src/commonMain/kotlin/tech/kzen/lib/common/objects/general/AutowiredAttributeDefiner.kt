package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


@Reflect
class AutowiredAttributeDefiner(
        private val weak: Boolean
): AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val forAttributeSegment = AttributeSegment.ofKey("for")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val attributeMetadata = graphStructure
                .graphMetadata
                .get(objectLocation)
                ?.attributes
                ?.values
                ?.get(attributeName)
                ?: throw IllegalArgumentException("Metadata not found: $objectLocation - $attributeName")

        val findIs = ObjectReference.parse(findIs(attributeMetadata))
        val objectReferenceHost = ObjectReferenceHost.ofLocation(objectLocation)
        val findIsLocation = graphStructure.graphNotation.coalesce.locate(findIs, objectReferenceHost)

        val references = mutableListOf<AttributeDefinition>()

        for ((location, notation) in graphStructure.graphNotation.coalesce.values) {
            val isReference = notation.attributes.values[NotationConventions.isAttributeName]?.asString()
                    ?: continue

            val isLocation = graphStructure.graphNotation.coalesce
                    .locate(ObjectReference.parse(isReference), objectReferenceHost)
            if (findIsLocation != isLocation) {
                continue
            }

            val attributeDefinition = defineLocation(location)

            references.add(attributeDefinition)
        }

        return AttributeDefinitionAttempt.success(
                ListAttributeDefinition(references))
    }


    private fun findIs(attributeMetadata: AttributeMetadata): String {
        val find = attributeMetadata.attributeMetadataNotation.values[forAttributeSegment]
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
        return when {
            weak ->
                ValueAttributeDefinition(objectLocation)
            
            else ->
                ReferenceAttributeDefinition(objectLocation.toReference())
        }
    }
}