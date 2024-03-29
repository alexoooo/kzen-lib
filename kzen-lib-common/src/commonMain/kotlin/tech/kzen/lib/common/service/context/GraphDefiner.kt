package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinitionFailure
import tech.kzen.lib.common.model.definition.ObjectDefinitionSuccess
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectCreator
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectDefiner
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.reflect.KClass


// TODO: convert to object?
class GraphDefiner {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val bootstrapObjects = GraphInstance(
            ObjectLocationMap(persistentMapOf(
                bootstrapEntry(DefaultConstructorObjectCreator),
                bootstrapEntry(DefaultConstructorObjectDefiner)
            )))

        fun isBootstrap(objectReference: ObjectReference): Boolean {
            return bootstrapObjects.objectInstances.locateOptional(objectReference) != null
        }

        private fun bootstrapEntry(bootstrapObject: Any): Pair<ObjectLocation, ObjectInstance> {
            val objectPath = bootstrapPath(bootstrapObject::class)
            return objectPath to ObjectInstance(bootstrapObject, AttributeNameMap.of())
        }


        private fun bootstrapPath(type: KClass<*>): ObjectLocation {
            return bootstrapPath(ObjectName(type.simpleName!!))
        }


        private fun bootstrapPath(objectName: ObjectName): ObjectLocation {
            return ObjectLocation(
                NotationConventions.kzenBasePath,
                ObjectPath(objectName, ObjectNesting.root))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun define(
        graphStructure: GraphStructure
    ): GraphDefinition {
        val attempt = tryDefine(graphStructure)
        require(! attempt.hasErrors()) {
            "Definition errors: ${attempt.failures}"
        }
        return attempt.successful()
    }


    fun tryDefine(
        graphStructure: GraphStructure
    ): GraphDefinitionAttempt {
        var definerAndRelatedInstances = bootstrapObjects

        val openDefinitions: MutableSet<ObjectLocation> = graphStructure
            .graphNotation
            .objectLocations
            .filter {
                it !in bootstrapObjects &&
                    ! isAbstract(it, graphStructure.graphNotation)
            }.toMutableSet()

        var closedDefinitions = GraphDefinition.empty
                .copy(graphStructure = graphStructure)

        val missingInstances = mutableSetOf<ObjectLocation>()

        val levelClosed = mutableSetOf<ObjectLocation>()
        val levelCreated = mutableSetOf<ObjectLocation>()
        val missingCreatorInstances = mutableSetOf<ObjectLocation>()
        val levelErrors = mutableMapOf<ObjectLocation, String>()
        val levelFailures = mutableMapOf<ObjectLocation, ObjectDefinitionFailure>()

        var levelCount = 0
        while (openDefinitions.isNotEmpty()) {
            levelCount += 1
            check(levelCount < 16) { "too deep" }
//            println("^^^^^ open - $levelCount: $openDefinitions")

            for (objectLocation in openDefinitions) {
//                if (objectLocation.objectPath.name.value == "StringHolderNullableNominal") {
//                    println("objectLocation: $objectLocation")
//                }
//                println("^^^^^ objectName: $objectLocation")

                val definerReference = definerReference(objectLocation, graphStructure.graphNotation)
                if (definerReference == null) {
                    levelErrors[objectLocation] = "Definer missing"
                    continue
                }

                val definerLocation = graphStructure.graphNotation.coalesce.locate(
                    definerReference, ObjectReferenceHost.ofLocation(objectLocation))
                val definer = definerAndRelatedInstances[definerLocation]?.reference as? ObjectDefiner

                if (definer == null) {
                    missingInstances.add(definerLocation)
                    continue
                }

                val definition = definer.define(
                    objectLocation,
                    graphStructure,
                    closedDefinitions,
                    definerAndRelatedInstances)
//                println("  >> definition: $definition")

                when (definition) {
                    is ObjectDefinitionSuccess -> {
                        closedDefinitions = closedDefinitions.put(objectLocation, definition.value)
                        levelClosed.add(objectLocation)
                    }

                    is ObjectDefinitionFailure -> {
                        levelErrors[objectLocation] = definition.errorMessage
                        missingInstances.addAll(definition.missingObjects.values)

                        levelFailures[objectLocation] = definition
                    }
                }
            }

//            println("--- missingInstances: $missingInstances")
            for (missingLocation in missingInstances) {
                val definition =
                    closedDefinitions[missingLocation]
                    ?: continue

//                println("  $$ got definition for: $missingName")
                val creatorLocation = graphStructure.graphNotation.coalesce.locate(
                    definition.creator)

                var hasMissingCreatorInstances = false
                if (creatorLocation !in definerAndRelatedInstances) {
                    missingCreatorInstances.add(creatorLocation)
                    hasMissingCreatorInstances = true

//                    println("  $$ missing creator ($missingName): $creatorLocation")
                }

                for (creatorRequired in definition.creatorDependencies) {
                    val creatorReferenceLocation =
                            graphStructure.graphNotation.coalesce.locate(creatorRequired)

                    if (creatorReferenceLocation !in definerAndRelatedInstances) {
                        missingCreatorInstances.add(creatorReferenceLocation)
                        hasMissingCreatorInstances = true

//                        println("  $$ missing creator reference ($missingName): " +
//                                "${definition.creator} - $creatorReferenceLocation")
                    }
                }

                if (hasMissingCreatorInstances) {
                    continue
                }

                val creator = definerAndRelatedInstances[creatorLocation]?.reference as ObjectCreator

                val instance = creator.create(
                    missingLocation,
                    graphStructure,
                    definition,
                    definerAndRelatedInstances)

//                println("  $$ created: $missingName")

                definerAndRelatedInstances = definerAndRelatedInstances.put(missingLocation, instance)
                levelCreated.add(missingLocation)
            }

            missingInstances.addAll(missingCreatorInstances)
            missingInstances.removeAll(levelCreated)

            if (levelClosed.isEmpty() && levelCreated.isEmpty()) {
//                for ((objectLocation, partialDefinition) in levelPartial) {
//                    closedDefinitions = closedDefinitions.put(objectLocation, partialDefinition)
//                }

                return GraphDefinitionAttempt(
                    closedDefinitions.objectDefinitions,
                    ObjectLocationMap(levelFailures.toPersistentMap()),
                    graphStructure)
            }

            openDefinitions.removeAll(levelClosed)

            levelCreated.clear()
            levelClosed.clear()
            missingCreatorInstances.clear()
            levelErrors.clear()
            levelFailures.clear()
        }

        return GraphDefinitionAttempt(
            closedDefinitions.objectDefinitions,
            ObjectLocationMap.empty(),
            graphStructure)
    }


    private fun definerReference(
            objectLocation: ObjectLocation,
            projectNotation: GraphNotation
    ): ObjectReference? {
        return projectNotation.firstAttribute(objectLocation, NotationConventions.definerAttributePath)
            ?.asString()
            ?.let { ObjectReference.parse(it) }
    }


    private fun isAbstract(
            objectName: ObjectLocation,
            projectNotation: GraphNotation
    ): Boolean {
        return projectNotation.directAttribute(objectName, NotationConventions.abstractAttributePath)
            ?.asBoolean()
            ?: false
    }
}