package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * not using {successful: GraphDefinition} to avoid implicitly elevating relation
 *  between objectDefinitions and graphStructure vs the failures
 */
data class GraphDefinitionAttempt(
    val objectDefinitions: ObjectLocationMap<ObjectDefinition>,
    val failures: ObjectLocationMap<ObjectDefinitionFailure>,
    val graphStructure: GraphStructure
) {
    fun successful(): GraphDefinition {
        return GraphDefinition(objectDefinitions, graphStructure)
    }


    val transitiveSuccessful: GraphDefinition by lazy {
        val failedObjectLocations = failures.map.keys.toMutableSet()
        var open = objectDefinitions.map

        var terminated = false
        while (!terminated) {
            terminated = true

            for (e in open) {
                val host = ObjectReferenceHost.ofLocation(e.key)
                val objectMetadata = graphStructure.graphMetadata.get(e.key)

                for (reference in e.value.references()) {
                    val objectReference = reference.objectReference

                    if (GraphDefiner.isBootstrap(objectReference)) {
                        continue
                    }

                    val failed =
                        if (objectReference.isEmpty()) {
                            val nullable = reference
                                .attributePath
                                ?.attribute
                                ?.let { objectMetadata?.attributes?.get(it) }
                                ?.type
                                ?.nullable
                                ?: false

                            !nullable
                        }
                        else {
                            val location = objectDefinitions.locateOptional(objectReference, host)

                            location == null ||
                                location in failedObjectLocations
                        }

                    if (failed) {
                        failedObjectLocations.add(e.key)
                        open = open.remove(e.key)
                        terminated = false
                        break
                    }
                }
            }
        }

        GraphDefinition(
            ObjectLocationMap(open), graphStructure)
    }


    /**
     * Why each object in notation is absent from [transitiveSuccessful]: direct definition failures pass
     *  through as-is; an object that defined but was pruned gets a synthesized failure naming, per
     *  reference, which attribute path, which reference, and what it was resolved against (host).
     *  Derivative drops (the reference resolves to another failed object) carry that object in
     *  [ObjectDefinitionFailure.missingObjects] - follow it for the root cause.
     *
     * Separate lazy so [transitiveSuccessful] (the hot path) stays untouched: a clean graph pays nothing,
     *  a broken one pays O(dropped x references) once per notation version.
     */
    val transitiveFailures: ObjectLocationMap<ObjectDefinitionFailure> by lazy {
        val successfulLocations = transitiveSuccessful.objectDefinitions.map.keys
        val dropped = objectDefinitions.map.keys - successfulLocations

        if (dropped.isEmpty() && failures.isEmpty()) {
            return@lazy ObjectLocationMap.empty()
        }

        val builder = mutableMapOf<ObjectLocation, ObjectDefinitionFailure>()
        builder.putAll(failures.map)

        for (objectLocation in dropped) {
            if (objectLocation in builder) {
                // direct definition failure, already the root cause
                continue
            }

            builder[objectLocation] = prunedFailure(objectLocation, successfulLocations)
        }

        ObjectLocationMap(builder.toPersistentMap())
    }


    private fun prunedFailure(
        objectLocation: ObjectLocation,
        successfulLocations: Set<ObjectLocation>
    ): ObjectDefinitionFailure {
        val definition = objectDefinitions[objectLocation]!!
        val host = ObjectReferenceHost.ofLocation(objectLocation)
        val objectMetadata = graphStructure.graphMetadata.get(objectLocation)

        val attributeFailures = mutableMapOf<AttributePath, AttributeDefinitionFailure>()
        val creatorCauses = mutableListOf<String>()
        val missingObjects = mutableSetOf<ObjectLocation>()

        fun cause(attributePath: AttributePath?, message: String, reference: ObjectReference) {
            if (attributePath == null) {
                creatorCauses.add(message)
            }
            else {
                attributeFailures[attributePath] = AttributeDefinitionFailure(message, reference, host)
            }
        }

        for (reference in definition.references()) {
            val objectReference = reference.objectReference

            if (GraphDefiner.isBootstrap(objectReference)) {
                continue
            }

            if (objectReference.isEmpty()) {
                // same nullability walk as transitiveSuccessful: absent metadata means non-nullable
                val nullable = reference
                    .attributePath
                    ?.attribute
                    ?.let { objectMetadata?.attributes?.get(it) }
                    ?.type
                    ?.nullable
                    ?: false

                if (!nullable) {
                    cause(reference.attributePath, "Required reference is empty", objectReference)
                }
                continue
            }

            val location = objectDefinitions.locateOptional(objectReference, host)
            when {
                location == null ->
                    cause(reference.attributePath, "Unresolved reference: $objectReference", objectReference)

                location !in successfulLocations -> {
                    missingObjects.add(location)
                    cause(reference.attributePath, "References failed object: $location", objectReference)
                }
            }
        }

        val attributeErrors = attributeFailures.entries.associate { (path, failure) ->
            val prefix = if (path.nesting.segments.isEmpty()) { "" } else { "${path.asString()}: " }
            path.attribute to prefix + failure.errorMessage
        }

        val summary = (attributeFailures.entries.map { (path, failure) ->
            "${path.asString()}: ${failure.errorMessage}"
        } + creatorCauses).joinToString("; ")

        return ObjectDefinitionFailure(
            definition,
            ObjectLocationSet(missingObjects),
            if (summary.isEmpty()) { "Dropped from the successful graph" } else { "Dropped - $summary" },
            attributeErrors,
            attributeFailures)
    }


    fun hasErrors(): Boolean {
        return failures.map.isNotEmpty()
    }
}