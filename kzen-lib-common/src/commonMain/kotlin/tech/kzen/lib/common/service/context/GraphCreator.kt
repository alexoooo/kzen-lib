package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationSet
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath


// TODO: convert to object?
class GraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    private data class UnsatisfiedReference(
        val objectReferenceHost: ObjectReferenceHost,
        val objectReference: ObjectReference
    ) {
        override fun toString(): String {
            return "$objectReference @ $objectReferenceHost"
        }
    }


    private data class UnsatisfiedSet(
        val locations: List<ObjectLocation>,
        val references: List<UnsatisfiedReference>
    )


    //-----------------------------------------------------------------------------------------------------------------
    fun createGraph(
        graphDefinition: GraphDefinition
    ): GraphInstance {
        val graphStructure = graphDefinition.graphStructure
        var partialObjectGraph = GraphDefiner.bootstrapObjects

        val closedLocator = ObjectLocationSet.Locator()

        val levels = constructionLevels(closedLocator, graphDefinition)

        for (objectLocation in levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions[objectLocation]
                    ?: throw IllegalArgumentException("Missing object definition: $objectLocation")

            val creatorPath = tryLocate(
                    closedLocator,
                    objectDefinition.creator,
                    ObjectReferenceHost.global
            ) ?: throw IllegalArgumentException("Unable to resolve: ${objectDefinition.creator}")

            val creator = partialObjectGraph[creatorPath]?.reference as? ObjectCreator
                    ?: throw IllegalArgumentException("ObjectCreator expected: ${objectDefinition.creator}")

            val instance = creator.create(
                    objectLocation,
                    graphStructure,
                    objectDefinition,
                    partialObjectGraph)

            partialObjectGraph = partialObjectGraph.put(objectLocation, instance)
        }

        return partialObjectGraph
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun constructionLevels(
        closedLocator: ObjectLocationSet.Locator,
        graphDefinition: GraphDefinition
    ): List<List<ObjectLocation>> {
        val closed = mutableSetOf<ObjectLocation>()
        closed.addAll(GraphDefiner.bootstrapObjects.keys)
        closedLocator.addAll(closed)

        val open = graphDefinition.objectDefinitions.values.keys.toMutableSet()

        val levels = mutableListOf<List<ObjectLocation>>()
        while (open.isNotEmpty()) {
            val nextLevel = findSatisfied(open, closed, closedLocator, graphDefinition)

            check(nextLevel.isNotEmpty()) {
                val unsatisfied = findUnsatisfied(open, closed, closedLocator, graphDefinition)
                "Unable to satisfy: $unsatisfied - Open = $open"
            }

            closed.addAll(nextLevel)
            closedLocator.addAll(nextLevel)

            @Suppress("ConvertArgumentToSet")
            open.removeAll(nextLevel)

            levels.add(nextLevel)
        }

        return levels
    }


    private fun findSatisfied(
        open: Set<ObjectLocation>,
        closed: Set<ObjectLocation>,
        closedLocator: ObjectLocationSet.Locator,
        graphDefinition: GraphDefinition
    ): List<ObjectLocation> {
        val allSatisfied = mutableListOf<ObjectLocation>()
        for (candidate in open) {
            val definition = graphDefinition.objectDefinitions[candidate]
                    ?: throw IllegalArgumentException("Missing definition: $candidate")

            val referenceHost = ObjectReferenceHost.ofLocation(candidate)

            val satisfied = definition
                    .references()
                    .map { reference -> tryLocate(closedLocator, reference, referenceHost) }
                    .all { location -> location in closed }

            if (! satisfied) {
//                println("not satisfied ($candidate): ${definition.references()}")
                continue
            }

//            println("satisfied: $candidate")
            allSatisfied.add(candidate)
        }
        return allSatisfied
    }


    private fun findUnsatisfied(
            open: Set<ObjectLocation>,
            closed: Set<ObjectLocation>,
            closedLocator: ObjectLocationSet.Locator,
            graphDefinition: GraphDefinition
    ): UnsatisfiedSet {
        val unsatisfiedLocations = mutableSetOf<ObjectLocation>()
        val unsatisfiedReferences =
                mutableListOf<UnsatisfiedReference>()

        for (candidate in open) {
            val definition = graphDefinition.objectDefinitions[candidate]
                    ?: throw IllegalArgumentException("Missing definition: $candidate")

            val referenceHost = ObjectReferenceHost.ofLocation(candidate)

            for (reference in definition.references()) {
                val location = tryLocate(closedLocator, reference, referenceHost)

                if (location == null) {
                    unsatisfiedReferences.add(UnsatisfiedReference(referenceHost, reference))
                }
                else if (location !in closed) {
                    unsatisfiedLocations.add(location)
                }
            }
        }

        return UnsatisfiedSet(unsatisfiedLocations.toList(), unsatisfiedReferences)
    }


    private fun tryLocate(
            closedLocator: ObjectLocationSet.Locator,
            reference: ObjectReference,
            referenceHost: ObjectReferenceHost
    ): ObjectLocation? {
        if (reference.hasPath()) {
            return ObjectLocation(
                    reference.path!!,
                    ObjectPath(reference.name, reference.nesting))
        }

        val objectLocations = closedLocator.locateAll(reference, referenceHost)

        if (objectLocations.values.isEmpty()) {
            return null
        }

        if (objectLocations.values.size == 1) {
            return objectLocations.values.iterator().next()
        }

        TODO("More than one candidate not supported yet")
    }
}