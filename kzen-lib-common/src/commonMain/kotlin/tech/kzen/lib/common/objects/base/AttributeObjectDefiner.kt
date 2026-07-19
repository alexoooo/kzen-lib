package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.GlobalMirror
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

    private val serviceAttributeCreator = ObjectReference.parse(
        ServiceAttributeCreator::class.simpleName!!)


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
        val attributeFailures = mutableMapOf<AttributePath, AttributeDefinitionFailure>()
        val missingObjects = mutableSetOf<ObjectLocation>()

        fun fail(attributeName: AttributeName, failure: AttributeDefinitionFailure) {
            attributeErrors[attributeName] = failure.errorMessage
            attributeFailures[AttributePath.ofName(attributeName)] = failure
        }

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            val attributeCreatorReference = attributeMetadata.creatorReference ?: defaultAttributeCreator
            creatorRequired.add(attributeCreatorReference)

            // definer references resolve against the global coalesce
            val attributeDefinerRef = attributeMetadata.definerReference ?: defaultAttributeDefiner
            val attributeDefinerLocation = graphStructure.graphNotation.coalesce.locateOptional(attributeDefinerRef)
            if (attributeDefinerLocation == null) {
                fail(attributeName, AttributeDefinitionFailure(
                    "Unknown attribute definer: $attributeDefinerRef",
                    attributeDefinerRef,
                    ObjectReferenceHost.global))
                continue
            }

            val definerInstance = partialGraphInstance[attributeDefinerLocation]
            if (definerInstance == null) {
                missingObjects.add(attributeDefinerLocation)
                fail(attributeName, AttributeDefinitionFailure(
                    "Definer missing: ${attributeDefinerLocation.objectPath.name}",
                    attributeDefinerRef,
                    ObjectReferenceHost.global))
                continue
            }

            val attributeDefiner = definerInstance.reference as? AttributeDefiner
            if (attributeDefiner == null) {
                fail(attributeName, AttributeDefinitionFailure(
                    "Attribute definer expected: $attributeDefinerRef",
                    attributeDefinerRef,
                    ObjectReferenceHost.global))
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
                    // pass the delegated failure through unchanged - it may carry its own structured fields
                    fail(attributeName, attributeDefinitionAttempt)
                }
            }
        }

        // @Service constructor parameters are not declared in notation; route each to the
        // ServiceAttributeCreator, which resolves it from the GraphEnvironment at creation time.
        // A notation-declared attribute of the same name wins (service entry skipped).
        for ((argumentName, serviceClassName) in GlobalMirror.serviceArguments(className)) {
            val attributeName = AttributeName(argumentName)
            if (attributeName in attributeDefinitions) {
                continue
            }
            attributeDefinitions[attributeName] = ServiceAttributeDefinition(serviceClassName)
            creatorRequired.add(serviceAttributeCreator)
        }

        val objectDefinition = partialDefinition()

        return when {
            missingObjects.isNotEmpty() -> {
                ObjectDefinitionAttempt.missingObjectsFailure(
                    "Unfulfilled dependency for: ${attributeErrors.keys.joinToString { it.value }}",
                    attributeErrors,
                    ObjectLocationSet(missingObjects),
                    objectDefinition,
                    attributeFailures)
            }

            attributeErrors.isNotEmpty() -> {
                ObjectDefinitionAttempt.failure(
                    "Failed attribute(s): ${attributeErrors.keys.joinToString { it.value }}",
                    attributeErrors,
                    objectDefinition,
                    attributeFailures)
            }

            else -> {
                ObjectDefinitionAttempt.success(objectDefinition)
            }
        }
    }
}