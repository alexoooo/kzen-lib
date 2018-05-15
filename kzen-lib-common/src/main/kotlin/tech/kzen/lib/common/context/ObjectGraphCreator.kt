package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata


object ObjectGraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    fun createGraph(
            graphDefinition: GraphDefinition,
            graphMetadata: GraphMetadata
    ): ObjectGraph {
        val objectInstances = mutableMapOf<String, Any>()
        objectInstances.putAll(ObjectGraphDefiner.bootstrapObjects)

        val objectGraph = ObjectGraph(objectInstances)

        val levels = constructionLevels(graphDefinition)

        for (name in levels.flatMap { it }) {
            val objectDefinition = graphDefinition.objectDefinitions[name]!!
            val objectMetadata = graphMetadata.objectMetadata[name]!!

            val creator = objectInstances[objectDefinition.creator] as? ObjectCreator
                    ?: throw IllegalArgumentException("ObjectCreator expected: ${objectDefinition.creator}")

            val instance = creator.create(
                    objectDefinition,
                    objectMetadata,
                    objectGraph)

            objectInstances[name] = instance
        }

        return ObjectGraph(objectInstances)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun constructionLevels(graphDefinition: GraphDefinition): List<List<String>> {
        val closed = mutableSetOf<String>()
        closed.addAll(ObjectGraphDefiner.bootstrapObjects.keys)

        val open = graphDefinition.objectDefinitions.keys.toMutableSet()

        val levels = mutableListOf<List<String>>()
        while (! open.isEmpty()) {
            val nextLevel = findSatisfied(open, closed, graphDefinition)

            check(! nextLevel.isEmpty(), {"unable to satisfy: $open"})

            closed.addAll(nextLevel)
            open.removeAll(nextLevel)

            levels.add(nextLevel)
        }

        return levels
    }


    private fun findSatisfied(
            open: Set<String>,
            closed: Set<String>,
            projectDefinition: GraphDefinition): List<String> {
        val allSatisfied = mutableListOf<String>()
        for (candidate in open) {
            val definition = projectDefinition.objectDefinitions[candidate]!!

            val satisfied = definition
                    .references()
                    .all { closed.contains(it) }

            if (! satisfied) {
                println("not satisfied ($candidate): ${definition.references()}")
                continue
            }

            println("satisfied: $candidate")
            allSatisfied.add(candidate)
        }
        return allSatisfied
    }
}