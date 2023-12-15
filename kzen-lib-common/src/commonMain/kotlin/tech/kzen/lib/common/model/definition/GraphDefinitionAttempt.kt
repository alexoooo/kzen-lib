package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphDefiner


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
        while (! terminated) {
            terminated = true

            for (e in open) {
//                if (e.key.objectPath.name.value == "DivisionOfPartial") {
//                    println("foo")
//                }

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

                            ! nullable
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


    fun hasErrors(): Boolean {
        return failures.map.isNotEmpty()
    }
}