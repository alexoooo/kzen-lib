package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation


class WeakAutowiredAttributeDefiner: AttributeDefiner {
    companion object {
        val findKey = AttributeSegment.ofKey("find")
//        val findAttribute = AttributePath.parse("find")
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinition {
        val attributeMetadata = graphStructure.graphMetadata.get(objectLocation).attributes[attributeName]
                ?: throw IllegalArgumentException("Not found: $objectLocation - $attributeName")

        val find = attributeMetadata.attributeMetadataNotation.values[findKey]
                as? MapAttributeNotation
                ?: throw IllegalArgumentException("Expected: $objectLocation - $attributeName - $findKey")

//        val isAbstract = find.get(NotationConventions.abstractSegment)?.asBoolean() ?: false
        val findIsReference = find.get(NotationConventions.isSegment)?.asString() ?: "Object"

        val references = mutableListOf<AttributeDefinition>()

        for ((location, notation) in graphStructure.graphNotation.coalesce.values) {
            val isReference = notation.attributes[NotationConventions.isName]?.asString() ?: "Object"

            if (findIsReference != isReference) {
                continue
            }

            references.add(
                    ValueAttributeDefinition(location))
        }

        return ListAttributeDefinition(references)
    }
}