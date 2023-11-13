package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.toPersistentMap


@Reflect
object AttributeObjectDefiner: ObjectDefiner
{
    //-----------------------------------------------------------------------------------------------------------------
    private val defaultAttributeDefiner = ObjectReference.parse(
        StructuralAttributeDefiner::class.simpleName!!)

    private val defaultAttributeCreator = ObjectReference.parse(
        DefinitionAttributeCreator::class.simpleName!!)


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
        objectLocation: ObjectLocation,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
        val objectMetadata = graphStructure.graphMetadata.objectMetadata[objectLocation]
            ?: return ObjectDefinitionAttempt.failure(
                "Metadata not found: $objectLocation",
                mapOf(),
                null)

        val classNameNotation = graphStructure.graphNotation
            .getString(objectLocation, NotationConventions.classAttributePath)
        val className = ClassName(classNameNotation)

        val creatorReferenceNotation = graphStructure.graphNotation
            .getString(objectLocation, NotationConventions.creatorAttributePath)
        val creatorReference = ObjectReference.parse(creatorReferenceNotation)

        val attributeDefinitions = mutableMapOf<AttributeName, AttributeDefinition>()
        val creatorRequired = mutableSetOf<ObjectReference>()

        fun partialDefinition() = ObjectDefinition(
            className,
            AttributeNameMap(attributeDefinitions.toPersistentMap()),
            creatorReference,
            creatorRequired)

        val attributeErrors = mutableMapOf<AttributeName, String>()
        val missingObjects = mutableSetOf<ObjectLocation>()

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.values) {
            val attributeCreatorReference = attributeMetadata.creatorReference ?: defaultAttributeCreator
            creatorRequired.add(attributeCreatorReference)

            val attributeDefinerRef = attributeMetadata.definerReference ?: defaultAttributeDefiner
            val attributeDefinerLocation = graphStructure.graphNotation.coalesce.locateOptional(attributeDefinerRef)
            if (attributeDefinerLocation == null) {
                attributeErrors[attributeName] = "Unknown attribute definer: $attributeDefinerRef"
                continue
            }

            val definerInstance = partialGraphInstance[attributeDefinerLocation]
            if (definerInstance == null) {
                missingObjects.add(attributeDefinerLocation)
                attributeErrors[attributeName] = "Definer missing: ${attributeDefinerLocation.objectPath.name}"
                continue
            }

            val attributeDefiner = definerInstance.reference as? AttributeDefiner
            if (attributeDefiner == null) {
                attributeErrors[attributeName] = "Attribute definer expected: $attributeDefinerRef"
                continue
            }

            val attributeDefinitionAttempt = attributeDefiner.define(
                objectLocation,
                attributeName,
                graphStructure,
                partialGraphDefinition,
                partialGraphInstance)

            when (attributeDefinitionAttempt) {
                is AttributeDefinitionSuccess -> {
                    attributeDefinitions[attributeName] = attributeDefinitionAttempt.value
                }

                is AttributeDefinitionFailure -> {
                    attributeErrors[attributeName] = attributeDefinitionAttempt.errorMessage
                }
            }
        }

        val objectDefinition = partialDefinition()

        return when {
            missingObjects.isNotEmpty() -> {
                ObjectDefinitionAttempt.missingObjectsFailure(
                    "Unfulfilled dependency : $attributeErrors",
                    attributeErrors,
                    ObjectLocationSet(missingObjects),
                    objectDefinition)
            }

            attributeErrors.isNotEmpty() -> {
                ObjectDefinitionAttempt.failure(
                    "Failed: ${attributeErrors.keys}",
                    attributeErrors,
                    objectDefinition)
            }

            else -> {
                ObjectDefinitionAttempt.success(objectDefinition)
            }
        }
    }
}