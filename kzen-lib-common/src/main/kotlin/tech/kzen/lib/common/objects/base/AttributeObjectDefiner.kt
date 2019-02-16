package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions


@Suppress("unused")
class AttributeObjectDefiner: ObjectDefiner {
    companion object {
        private val creatorParameter = AttributePath.ofAttribute(AttributeName("creator"))

//        private val defaultParameterDefiner =
//                NotationParameterDefiner::class.simpleName!!

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
//                ?: throw IllegalArgumentException("Metadata not found: $objectName")

        val className = graphStructure.graphNotation.getString(objectLocation, NotationConventions.classAttribute)

        val constructorArguments = mutableMapOf<AttributeName, AttributeDefinition>()
        val parameterCreators = mutableSetOf<ObjectReference>()

//        val argumentNames = Mirror.constructorArgumentNames(className)
//        println("&&& class arguments ($className): $argumentNames")

        for ((attributeName, attributeMetadata) in objectMetadata.attributes) {
            val attributeCreatorReference = attributeMetadata.creatorReference ?: defaultAttributeCreator
            parameterCreators.add(attributeCreatorReference)

            val attributeDefinerRef = attributeMetadata.definerReference ?: defaultAttributeDefiner
            val attributeDefinerLocation = graphStructure.graphNotation.coalesce
                    .locateOptional(objectLocation, attributeDefinerRef)
                    ?: return ObjectDefinitionAttempt.failure("Unknown parameter definer: $attributeDefinerRef")

            val definerInstance = partialGraphInstance.objects
                    .find(attributeDefinerLocation)
                    ?: return ObjectDefinitionAttempt.missingObjectsFailure(
                            setOf(attributeDefinerLocation))

            val attributeDefiner = definerInstance
                    as? AttributeDefiner
                    ?: return ObjectDefinitionAttempt.failure(
                            "Attribute Definer expected: $attributeDefinerRef")

            val attributeDefinition = attributeDefiner.define(
                    objectLocation,
                    attributeName,
                    graphStructure,
                    partialGraphDefinition,
                    partialGraphInstance)

            constructorArguments[attributeName] = attributeDefinition
        }

        val creatorReference = ObjectReference.parse(
                graphStructure.graphNotation.getString(objectLocation, creatorParameter))

        val objectDefinition = ObjectDefinition(
                className,
                constructorArguments,
                creatorReference,
                parameterCreators)

        return ObjectDefinitionAttempt.success(objectDefinition)
    }
}