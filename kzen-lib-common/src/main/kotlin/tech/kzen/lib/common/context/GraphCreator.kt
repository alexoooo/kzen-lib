package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationSet
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure


object GraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    fun createGraph(
            graphStructure: GraphStructure,
            graphDefinition: GraphDefinition
    ): GraphInstance {
//        val objectInstances = mutableMapOf<ObjectLocation, Any>()
//        objectInstances.putAll(GraphDefiner.bootstrapObjects)

        var partialObjectGraph = GraphDefiner.bootstrapObjects

        val levels = constructionLevels(graphDefinition)

        for (objectLocation in levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions[objectLocation]
                    ?: throw IllegalArgumentException("Missing object definition: $objectLocation")

            val creatorPath = tryLocate(
                    graphStructure.graphMetadata.objectMetadata.values.keys,
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
    private fun constructionLevels(graphDefinition: GraphDefinition): List<List<ObjectLocation>> {
        val closed = mutableSetOf<ObjectLocation>()
        closed.addAll(GraphDefiner.bootstrapObjects.keys)

        val open = graphDefinition.objectDefinitions.values.keys.toMutableSet()

        val levels = mutableListOf<List<ObjectLocation>>()
        while (open.isNotEmpty()) {
            val nextLevel = findSatisfied(open, closed, graphDefinition)

            check(nextLevel.isNotEmpty()) {
                "unable to satisfy: $open"
            }

            closed.addAll(nextLevel)
            open.removeAll(nextLevel)

            levels.add(nextLevel)
        }

        return levels
    }


    private fun findSatisfied(
            open: Set<ObjectLocation>,
            closed: Set<ObjectLocation>,
            graphDefinition: GraphDefinition
    ): List<ObjectLocation> {
        val allSatisfied = mutableListOf<ObjectLocation>()
        for (candidate in open) {
            val definition = graphDefinition.objectDefinitions[candidate]
                    ?: throw IllegalArgumentException("Missing definition: $candidate")

            val referenceHost = ObjectReferenceHost.ofLocation(candidate)

            val satisfied = definition
                    .references()
                    .map { reference -> tryLocate(closed, reference, referenceHost) }
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


    private fun tryLocate(
            closed: Set<ObjectLocation>,
            reference: ObjectReference,
            referenceHost: ObjectReferenceHost
    ): ObjectLocation? {
        if (reference.isAbsolute()) {
            return ObjectLocation(
                    reference.path!!,
                    ObjectPath(reference.name, reference.nesting!!))
        }

        val objectLocations = ObjectLocationSet.locateAll(closed, reference, referenceHost)

        if (objectLocations.values.isEmpty()) {
            return null
        }

        if (objectLocations.values.size == 1) {
            return objectLocations.values.iterator().next()
        }

        TODO("More than one candidate not supported yet")
    }
}