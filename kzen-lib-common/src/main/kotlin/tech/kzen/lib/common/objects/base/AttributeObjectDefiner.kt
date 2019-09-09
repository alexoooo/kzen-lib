package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.context.definition.AttributeDefinition
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ObjectDefinition
import tech.kzen.lib.common.context.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationSet
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.toPersistentMap


@Suppress("unused")
class AttributeObjectDefiner: ObjectDefiner {
    companion object {
        private val defaultAttributeDefiner = ObjectReference.parse(
                StructuralAttributeDefiner::class.simpleName!!)

        private val defaultAttributeCreator = ObjectReference.parse(
                DefinitionAttributeCreator::class.simpleName!!)
    }


    override fun define(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
        val objectMetadata = graphStructure.graphMetadata.objectMetadata.get(objectLocation)
                ?: throw IllegalArgumentException("Metadata not found: $objectLocation")

        val className = ClassName(graphStructure.graphNotation
                .getString(objectLocation, NotationConventions.classAttributePath))

        val attributeDefinitions = mutableMapOf<AttributeName, AttributeDefinition>()
        val creatorRequired = mutableSetOf<ObjectReference>()

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.values) {
            val attributeCreatorReference = attributeMetadata.creatorReference ?: defaultAttributeCreator
            creatorRequired.add(attributeCreatorReference)

            val attributeDefinerRef = attributeMetadata.definerReference ?: defaultAttributeDefiner
            val attributeDefinerLocation = graphStructure.graphNotation.coalesce
                    .locateOptional(attributeDefinerRef)
                    ?: return ObjectDefinitionAttempt.failure(
                            "Unknown attribute definer: $attributeDefinerRef")

            val definerInstance = partialGraphInstance[attributeDefinerLocation]
                    ?: return ObjectDefinitionAttempt.missingObjectsFailure(
                            ObjectLocationSet(setOf(attributeDefinerLocation)))

            val attributeDefiner = definerInstance.reference
                    as? AttributeDefiner
                    ?: return ObjectDefinitionAttempt.failure(
                            "Attribute definer expected: $attributeDefinerRef")

            val attributeDefinitionAttempt = attributeDefiner.define(
                    objectLocation,
                    attributeName,
                    graphStructure,
                    partialGraphDefinition,
                    partialGraphInstance)

            val attributeDefinition = attributeDefinitionAttempt.value
                    ?: return ObjectDefinitionAttempt.failure(
                            "Attribute definition failed $attributeName: " +
                                    "${attributeDefinitionAttempt.errorMessage}")

            attributeDefinitions[attributeName] = attributeDefinition
        }

        val creatorReference = ObjectReference.parse(
                graphStructure.graphNotation.getString(objectLocation, NotationConventions.creatorAttributePath))

        val objectDefinition = ObjectDefinition(
                className,
                AttributeNameMap(attributeDefinitions.toPersistentMap()),
                creatorReference,
                creatorRequired)

        return ObjectDefinitionAttempt.success(objectDefinition)
    }
}