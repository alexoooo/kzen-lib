package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectMap
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.metadata.model.GraphMetadata


object ObjectGraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    fun createGraph(
            graphDefinition: GraphDefinition,
            graphMetadata: GraphMetadata
    ): ObjectGraph {
        val objectInstances = mutableMapOf<ObjectLocation, Any>()
        objectInstances.putAll(ObjectGraphDefiner.bootstrapObjects)

        val objectGraph = ObjectGraph(ObjectMap(objectInstances))

        val levels = constructionLevels(graphDefinition)

        for (objectLocation in levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions.get(objectLocation)
            val objectMetadata = graphMetadata.get(objectLocation)

            val creatorPath = tryLocate(graphMetadata.objectMetadata.values.keys, objectDefinition.creator)
                    ?: throw IllegalArgumentException("Unable to resolve: ${objectDefinition.creator}")

            val creator = objectInstances[creatorPath] as? ObjectCreator
                    ?: throw IllegalArgumentException("ObjectCreator expected: ${objectDefinition.creator}")

            val instance = creator.create(
                    objectLocation,
                    objectDefinition,
                    objectMetadata,
                    objectGraph)

            objectInstances[objectLocation] = instance
        }

        return objectGraph
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun constructionLevels(graphDefinition: GraphDefinition): List<List<ObjectLocation>> {
        val closed = mutableSetOf<ObjectLocation>()
        closed.addAll(ObjectGraphDefiner.bootstrapObjects.keys)

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

            val satisfied = definition
                    .references()
                    .map { tryLocate(closed, it) }
                    .all { it != null && closed.contains(it) }

            if (! satisfied) {
                println("not satisfied ($candidate): ${definition.references()}")
                continue
            }

            println("satisfied: $candidate")
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