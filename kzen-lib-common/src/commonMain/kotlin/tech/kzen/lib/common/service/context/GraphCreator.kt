package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


object GraphCreator {
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
        graphDefinition: GraphDefinition,
        environment: GraphEnvironment = GraphEnvironment.empty
    ): GraphInstance {
        val graphStructure = graphDefinition.graphStructure
        var partialObjectGraph = GraphDefiner.bootstrapObjects

        val locator = ObjectLocationSet.Locator()

        val levels = constructionLevels(locator, graphDefinition, graphStructure.graphMetadata)

        for (objectLocation in levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions[objectLocation]
                ?: throw IllegalArgumentException("Missing object definition: $objectLocation")

            val creatorPath = tryLocate(
                locator,
                objectDefinition.creator,
                ObjectReferenceHost.global
            ) ?: throw IllegalArgumentException("Unable to resolve: ${objectDefinition.creator}")

            val creator = partialObjectGraph[creatorPath]?.reference as? ObjectCreator
                ?: throw IllegalArgumentException("ObjectCreator expected: ${objectDefinition.creator}")

            val instance = creator.create(
                    objectLocation,
                    graphStructure,
                    objectDefinition,
                    partialObjectGraph,
                    environment)

            partialObjectGraph = partialObjectGraph.put(objectLocation, instance)
        }

        return partialObjectGraph
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Kahn's algorithm, O(V+E): resolve each declared reference once against the full location set,
     * then peel zero-indegree levels. An object is in level k iff all of its dependencies are in
     * levels < k, and in-level order is deterministic (definition insertion order). A nullable empty
     * reference contributes no edge; a required empty reference, an unresolvable reference, a
     * path-qualified reference with no backing definition, and any reference cycle each block their
     * object permanently, which surfaces through the unsatisfied-set diagnostics.
     */
    private fun constructionLevels(
        locator: ObjectLocationSet.Locator,
        graphDefinition: GraphDefinition,
        graphMetadata: GraphMetadata
    ): List<List<ObjectLocation>> {
        val bootstrapLocations = GraphDefiner.bootstrapObjects.keys
        val objectDefinitions = graphDefinition.objectDefinitions.map

        locator.addAll(bootstrapLocations)
        locator.addAll(objectDefinitions.keys)

        val ordinals = mutableMapOf<ObjectLocation, Int>()
        for (objectLocation in objectDefinitions.keys) {
            ordinals[objectLocation] = ordinals.size
        }

        val dependents = mutableMapOf<ObjectLocation, MutableList<ObjectLocation>>()
        val remainingBlockers = mutableMapOf<ObjectLocation, Int>()
        for (objectLocation in objectDefinitions.keys) {
            remainingBlockers[objectLocation] = 0
        }

        for ((objectLocation, definition) in objectDefinitions) {
            val referenceHost = ObjectReferenceHost.ofLocation(objectLocation)

            val objectMetadata = graphMetadata.get(objectLocation)
                ?: throw IllegalArgumentException("Missing metadata: $objectLocation")

            for (reference in definition.references()) {
                if (reference.objectReference.isEmpty()) {
                    if (!reference.isNullable(objectMetadata)) {
                        remainingBlockers[objectLocation] = remainingBlockers[objectLocation]!! + 1
                    }
                    continue
                }

                val dependencyLocation = tryLocate(locator, reference.objectReference, referenceHost)

                if (dependencyLocation == null ||
                        dependencyLocation !in objectDefinitions && dependencyLocation !in bootstrapLocations) {
                    remainingBlockers[objectLocation] = remainingBlockers[objectLocation]!! + 1
                    continue
                }

                if (dependencyLocation in bootstrapLocations) {
                    continue
                }

                dependents.getOrPut(dependencyLocation) { mutableListOf() }.add(objectLocation)
                remainingBlockers[objectLocation] = remainingBlockers[objectLocation]!! + 1
            }
        }

        val open = objectDefinitions.keys.toMutableSet()
        val levels = mutableListOf<List<ObjectLocation>>()

        var nextLevel = remainingBlockers.filterValues { it == 0 }.keys.toList()
        while (nextLevel.isNotEmpty()) {
            levels.add(nextLevel)

            val following = mutableListOf<ObjectLocation>()
            for (peeled in nextLevel) {
                open.remove(peeled)

                for (dependent in dependents[peeled].orEmpty()) {
                    val remaining = remainingBlockers[dependent]!! - 1
                    remainingBlockers[dependent] = remaining
                    if (remaining == 0) {
                        following.add(dependent)
                    }
                }
            }

            nextLevel = following.sortedBy { ordinals[it] }
        }

        check(open.isEmpty()) {
            val closed = bootstrapLocations + (objectDefinitions.keys - open)
            val unsatisfied = findUnsatisfied(open, closed, locator, graphDefinition)
            "Unable to satisfy: $unsatisfied - Open = $open"
        }

        return levels
    }


    private fun findUnsatisfied(
            open: Set<ObjectLocation>,
            closed: Set<ObjectLocation>,
            locator: ObjectLocationSet.Locator,
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
                val location = tryLocate(
                    locator, reference.objectReference, referenceHost)

                if (location == null) {
                    unsatisfiedReferences.add(
                        UnsatisfiedReference(referenceHost, reference.objectReference))
                }
                else if (location !in closed) {
                    unsatisfiedLocations.add(location)
                }
            }
        }

        return UnsatisfiedSet(unsatisfiedLocations.toList(), unsatisfiedReferences)
    }


    private fun tryLocate(
        locator: ObjectLocationSet.Locator,
        reference: ObjectReference,
        referenceHost: ObjectReferenceHost
    ): ObjectLocation? {
        if (reference.hasPath() && reference.name.objectName != null) {
            return ObjectLocation(
                    reference.path!!,
                    ObjectPath(reference.name.objectName, reference.nesting))
        }

        val objectLocations = locator.locateAll(reference, referenceHost)

        if (objectLocations.values.isEmpty()) {
            return null
        }

        if (objectLocations.values.size == 1) {
            return objectLocations.values.iterator().next()
        }

        throw IllegalArgumentException(
            "Ambiguous reference: $reference @ $referenceHost - candidates: ${objectLocations.values}")
    }
}