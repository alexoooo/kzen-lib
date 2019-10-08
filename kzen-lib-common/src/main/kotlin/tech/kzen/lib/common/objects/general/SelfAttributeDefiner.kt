package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


@Suppress("unused")
class SelfAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val objectMetadata = graphStructure.graphMetadata.get(objectLocation)
                ?: return AttributeDefinitionAttempt.failure(
                        "Metadata missing: $objectLocation")

        val attributeMetadata = objectMetadata.attributes[attributeName]
                ?: return AttributeDefinitionAttempt.failure(
                        "Attribute metadata missing: $objectLocation - $attributeName")

        val attributeType = attributeMetadata.type
                ?: return AttributeDefinitionAttempt.failure(
                        "Attribute type missing: $objectLocation - $attributeName")

        val attributeValue = when (attributeType.className) {
            ObjectLocation.className ->
                objectLocation

            ObjectNotation.className ->
                graphStructure.graphNotation.coalesce[objectLocation]
                        ?: return AttributeDefinitionAttempt.failure(
                                "Object notation missing: $objectLocation")

            DocumentNotation.className ->
                graphStructure.graphNotation.documents[objectLocation.documentPath]
                        ?: return AttributeDefinitionAttempt.failure(
                                "Document notation missing: ${objectLocation.documentPath}")

            else ->
                return AttributeDefinitionAttempt.failure(
                        "Unknown self value type: $attributeType - $objectLocation - $attributeName")
        }

        return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(
                        attributeValue))
    }
}