package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.collect.toPersistentMap


object GraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    fun createGraph(
            graphStructure: GraphStructure,
            graphDefinition: GraphDefinition
    ): GraphInstance {
//        val objectInstances = mutableMapOf<ObjectLocation, Any>()
//        objectInstances.putAll(GraphDefiner.bootstrapObjects)

        var partialObjectGraph = GraphInstance(ObjectLocationMap(
                GraphDefiner.bootstrapObjects.toPersistentMap()))

        val levels = constructionLevels(graphDefinition)

        for (objectLocation in levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions.get(objectLocation)
                    ?: throw IllegalArgumentException("Missing object definition: $objectLocation")

            val creatorPath = tryLocate(
                    graphStructure.graphMetadata.objectMetadata.values.keys, objectDefinition.creator
            ) ?: throw IllegalArgumentException("Unable to resolve: ${objectDefinition.creator}")

            val creator = partialObjectGraph[creatorPath] as? ObjectCreator
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
        while (! open.isEmpty()) {
            val nextLevel = findSatisfied(open, closed, graphDefinition)

            check(! nextLevel.isEmpty()) {"unable to satisfy: $open"}

            closed.addAll(nextLevel)
            open.removeAll(nextLevel)

            levels.add(nextLevel)
        }

        return levels
    }


    private fun findSatisfied(
            open: Set<ObjectLocation>,
            closed: Set<ObjectLocation>,
            projectDefinition: GraphDefinition): List<ObjectLocation> {
        val allSatisfied = mutableListOf<ObjectLocation>()
        for (candidate in open) {
            val definition = projectDefinition.objectDefinitions.get(candidate)
                    ?: throw IllegalArgumentException("Missing definition: $candidate")

            val satisfied = definition
                    .references()
                    .map { tryLocate(closed, it) }
                    .all { it != null && closed.contains(it) }

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
            reference: ObjectReference
    ): ObjectLocation? {
        if (reference.isAbsolute()) {
            return ObjectLocation(
                    reference.path!!,
                    ObjectPath(reference.name, reference.nesting!!))
        }

        val candidates = mutableListOf<ObjectLocation>()
        for (objectPath in closed) {
            if (objectPath.objectPath.name == reference.name) {
                candidates.add(objectPath)
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        if (candidates.size == 1) {
            return candidates.iterator().next()
        }

        TODO("More than one candidate not supported yet")
    }
}