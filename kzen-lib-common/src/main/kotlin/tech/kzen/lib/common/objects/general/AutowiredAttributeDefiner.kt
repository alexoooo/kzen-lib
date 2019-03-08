package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.objects.bootstrap.BootstrapConventions
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation


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
                ?: throw IllegalArgumentException("Not found: $objectLocation - $attributeName")

        val find = attributeMetadata.attributeMetadataNotation.values[findSegment]
                as? MapAttributeNotation
                ?: throw IllegalArgumentException("Expected: $objectLocation - $attributeName - $findSegment")

//        val isAbstract = find.get(NotationConventions.abstractSegment)?.asBoolean() ?: false
        val findIsReference = find.get(NotationConventions.isSegment)?.asString()
                ?: BootstrapConventions.rootObjectName.value

        val references = mutableListOf<AttributeDefinition>()

        for ((location, notation) in graphStructure.graphNotation.coalesce.values) {
            val isReference = notation.attributes[NotationConventions.isName]?.asString()
                    ?: BootstrapConventions.rootObjectName.value

            if (findIsReference != isReference) {
                continue
            }

            val attributeDefinition = defineLocation(location)

            references.add(attributeDefinition)
        }

        return ListAttributeDefinition(references)
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