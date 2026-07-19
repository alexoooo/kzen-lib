package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinitionReference
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.GraphInstanceAttempt
import tech.kzen.lib.common.model.instance.ObjectCreationFailure
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.platform.collect.toPersistentMap


object GraphCreator {
    //-----------------------------------------------------------------------------------------------------------------
    private data class Leveling(
        val levels: List<List<ObjectLocation>>,

        /** Objects that never reached zero in-degree: unsatisfiable references and/or reference cycles. */
        val open: Set<ObjectLocation>,

        val closed: Set<ObjectLocation>
    )


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Every object that could be created, plus a per-object [ObjectCreationFailure] for each that could not.
     *  A failing object does not abort the pass - independent objects are still created (creators are
     *  construction-pure by contract), and dependents of a failure are skipped with the origin recorded.
     *
     * An ambiguous reference is a graph-shape error rather than an object-attributable one, so it still
     *  propagates as IllegalArgumentException.
     */
    fun tryCreateGraph(
        graphDefinition: GraphDefinition,
        environment: GraphEnvironment = GraphEnvironment.empty
    ): GraphInstanceAttempt {
        val graphStructure = graphDefinition.graphStructure
        var partialObjectGraph = GraphDefiner.bootstrapObjects

        val locator = ObjectLocationSet.Locator()

        val leveling = constructionLevels(locator, graphDefinition, graphStructure.graphMetadata)

        val failures = mutableMapOf<ObjectLocation, ObjectCreationFailure>()
        failures.putAll(unsatisfiedFailures(leveling, locator, graphDefinition))

        for (objectLocation in leveling.levels.flatten()) {
            val objectDefinition = graphDefinition.objectDefinitions[objectLocation]
            if (objectDefinition == null) {
                failures[objectLocation] = ObjectCreationFailure("Missing object definition")
                continue
            }

            if (failures.isNotEmpty()) {
                val failedDependencies = failedDependencies(
                    objectDefinition, objectLocation, locator, failures.keys)

                if (failedDependencies.isNotEmpty()) {
                    failures[objectLocation] = ObjectCreationFailure(
                        "Dependency creation failed: ${failedDependencies.joinToString()}",
                        failedDependencies = failedDependencies)
                    continue
                }
            }

            val creatorPath = tryLocate(locator, objectDefinition.creator, ObjectReferenceHost.global)
            if (creatorPath == null) {
                failures[objectLocation] = ObjectCreationFailure(
                    "Unable to resolve creator: ${objectDefinition.creator}")
                continue
            }

            val creator = partialObjectGraph[creatorPath]?.reference as? ObjectCreator
            if (creator == null) {
                failures[objectLocation] = ObjectCreationFailure(
                    "ObjectCreator expected: ${objectDefinition.creator}")
                continue
            }

            val instance =
                try {
                    creator.create(
                        objectLocation,
                        graphStructure,
                        objectDefinition,
                        partialObjectGraph,
                        environment)
                }
                catch (t: Throwable) {
                    failures[objectLocation] = ObjectCreationFailure(
                        "Creation failed: ${t::class.simpleName}: ${t.message}")
                    continue
                }

            partialObjectGraph = partialObjectGraph.put(objectLocation, instance)
        }

        return GraphInstanceAttempt(
            partialObjectGraph,
            ObjectLocationMap(failures.toPersistentMap()))
    }


    fun createGraph(
        graphDefinition: GraphDefinition,
        environment: GraphEnvironment = GraphEnvironment.empty
    ): GraphInstance {
        val attempt = tryCreateGraph(graphDefinition, environment)

        check(!attempt.hasErrors()) {
            val detail = attempt.failures.map.entries.joinToString(", ") { (location, failure) ->
                "$location - ${failure.errorMessage}"
            }
            "Unable to create graph: $detail"
        }

        return attempt.objectInstances
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Kahn's algorithm, O(V+E): resolve each declared reference once against the full location set,
     * then peel zero-indegree levels. An object is in level k iff all of its dependencies are in
     * levels < k, and in-level order is deterministic (definition insertion order). A nullable empty
     * reference contributes no edge; a required empty reference, an unresolvable reference, a
     * path-qualified reference with no backing definition, and any reference cycle each block their
     * object permanently, which surfaces as [Leveling.open] and is attributed per object by
     * [unsatisfiedFailures].
     */
    private fun constructionLevels(
        locator: ObjectLocationSet.Locator,
        graphDefinition: GraphDefinition,
        graphMetadata: GraphMetadata
    ): Leveling {
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

        return Leveling(
            levels,
            open,
            bootstrapLocations + (objectDefinitions.keys - open))
    }


    /**
     * Attributes each leftover of [Leveling.open] to its own cause: an unresolvable (or required-but-empty)
     *  reference makes the object itself unsatisfiable, whereas a reference into another leftover makes it
     *  merely blocked. A reference cycle surfaces as mutual [ObjectCreationFailure.failedDependencies].
     */
    private fun unsatisfiedFailures(
        leveling: Leveling,
        locator: ObjectLocationSet.Locator,
        graphDefinition: GraphDefinition
    ): Map<ObjectLocation, ObjectCreationFailure> {
        if (leveling.open.isEmpty()) {
            return mapOf()
        }

        val graphMetadata = graphDefinition.graphStructure.graphMetadata
        val builder = mutableMapOf<ObjectLocation, ObjectCreationFailure>()

        for (candidate in leveling.open) {
            val definition = graphDefinition.objectDefinitions[candidate]
                ?: throw IllegalArgumentException("Missing definition: $candidate")

            val referenceHost = ObjectReferenceHost.ofLocation(candidate)
            val objectMetadata = graphMetadata.get(candidate)

            val unsatisfiedReferences = mutableListOf<ObjectCreationFailure.UnsatisfiedReference>()
            val blockedBy = mutableSetOf<ObjectLocation>()

            for (reference in definition.references()) {
                val objectReference = reference.objectReference

                if (objectReference.isEmpty()) {
                    val nullable = objectMetadata?.let { reference.isNullable(it) } ?: false
                    if (!nullable) {
                        unsatisfiedReferences.add(unsatisfiedReference(reference, referenceHost))
                    }
                    continue
                }

                val location = tryLocate(locator, objectReference, referenceHost)
                when {
                    location == null ->
                        unsatisfiedReferences.add(unsatisfiedReference(reference, referenceHost))

                    location !in leveling.closed ->
                        blockedBy.add(location)
                }
            }

            val parts = mutableListOf<String>()
            if (unsatisfiedReferences.isNotEmpty()) {
                parts.add("unresolved ${unsatisfiedReferences.joinToString()}")
            }
            if (blockedBy.isNotEmpty()) {
                parts.add("blocked by ${blockedBy.joinToString()}")
            }

            builder[candidate] = ObjectCreationFailure(
                "Unsatisfiable: ${parts.joinToString("; ")}",
                unsatisfiedReferences,
                blockedBy)
        }

        return builder
    }


    private fun unsatisfiedReference(
        reference: ObjectDefinitionReference,
        referenceHost: ObjectReferenceHost
    ): ObjectCreationFailure.UnsatisfiedReference {
        return ObjectCreationFailure.UnsatisfiedReference(
            reference.objectReference, reference.attributePath, referenceHost)
    }


    private fun failedDependencies(
        objectDefinition: ObjectDefinition,
        objectLocation: ObjectLocation,
        locator: ObjectLocationSet.Locator,
        failedLocations: Set<ObjectLocation>
    ): Set<ObjectLocation> {
        val referenceHost = ObjectReferenceHost.ofLocation(objectLocation)
        val builder = mutableSetOf<ObjectLocation>()

        for (reference in objectDefinition.references()) {
            if (reference.objectReference.isEmpty()) {
                continue
            }

            val location = tryLocate(locator, reference.objectReference, referenceHost)
            if (location != null && location in failedLocations) {
                builder.add(location)
            }
        }

        return builder
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
