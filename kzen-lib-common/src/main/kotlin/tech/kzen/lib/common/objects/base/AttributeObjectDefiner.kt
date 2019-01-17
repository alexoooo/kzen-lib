package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.platform.Mirror


class AttributeObjectDefiner: ObjectDefiner {
    companion object {
        private val creatorParameter = AttributePath.ofAttribute(AttributeName("creator"))

//        private val defaultParameterDefiner =
//                NotationParameterDefiner::class.simpleName!!

        private val defaultAttributeDefiner =
                NotationAttributeDefiner::class.simpleName!!

        private val defaultAttributeCreator =
                StructuralAttributeCreator::class.simpleName!!
    }


    override fun define(
            objectLocation: ObjectLocation,
            notationTree: NotationTree,
            graphMetadata: GraphMetadata,
            graphDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ObjectDefinitionAttempt {
        val objectMetadata = graphMetadata.objectMetadata.get(objectLocation)
//                ?: throw IllegalArgumentException("Metadata not found: $objectName")

        val className = notationTree.getString(objectLocation, NotationConventions.classAttribute)

        val constructorArguments = mutableMapOf<AttributeName, AttributeDefinition>()
        val parameterCreators = mutableSetOf<ObjectReference>()

        val argumentNames = Mirror.constructorArgumentNames(className)
//        println("&&& class arguments ($className): $argumentNames")

        for (arg in argumentNames) {
            val attributeName = AttributeName(arg)

            val parameterMetadata = objectMetadata.attributes[attributeName]
                    ?: throw IllegalArgumentException(
                            "Argument not found in metadata ($attributeName): $objectMetadata")

            val parameterCreatorReference = parameterMetadata.creator ?: defaultAttributeCreator
            parameterCreators.add(ObjectReference.parse(parameterCreatorReference))

            val parameterDefinerRef = parameterMetadata.definer ?: defaultAttributeDefiner
            val parameterDefinerLocation = notationTree.coalesce
                    .locateOptional(objectLocation, ObjectReference.parse(parameterDefinerRef))
                    ?: return ObjectDefinitionAttempt.failure("Unknown parameter definer: $parameterDefinerRef")

            val definerInstance = objectGraph.objects
                    .find(parameterDefinerLocation)
                    ?: return ObjectDefinitionAttempt.missingObjectsFailure(
                            setOf(parameterDefinerLocation))

            val parameterDefiner = definerInstance
                    as? AttributeDefiner
                    ?: return ObjectDefinitionAttempt.failure(
                            "Parameter Definer expected: $parameterDefinerRef")

            val parameterDefinition = parameterDefiner.define(
                    objectLocation,
                    attributeName,
                    notationTree,
                    graphMetadata,
                    graphDefinition,
                    objectGraph)

            constructorArguments[attributeName] = parameterDefinition
        }

        val creatorName = notationTree.getString(objectLocation, creatorParameter)
        val creatorReference = ObjectReference.parse(creatorName)

        val objectDefinition = ObjectDefinition(
                className,
                constructorArguments,
                creatorReference,
                parameterCreators)

        return ObjectDefinitionAttempt.success(objectDefinition)
    }
}