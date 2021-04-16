package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphDefiner


data class GraphDefinitionAttempt(
    val objectDefinitions: ObjectLocationMap<ObjectDefinition>,
    val failures: ObjectLocationMap<ObjectDefinitionFailure>,
    val graphStructure: GraphStructure
) {
    fun successful(): GraphDefinition {
        return GraphDefinition(objectDefinitions, graphStructure)
    }


    fun transitiveSuccessful(): GraphDefinition {
        val failedObjectLocations = failures.values.keys.toMutableSet()
        var open = objectDefinitions.values

        var terminated = false
        while (! terminated) {
            terminated = true

            for (e in open) {
                val host = ObjectReferenceHost.ofLocation(e.key)
                for (objectReference in e.value.references()) {
                    if (GraphDefiner.bootstrapObjects.objectInstances.locateOptional(objectReference) != null) {
                        continue
                    }

                    val location = objectDefinitions.locateOptional(objectReference, host)
                    if (location == null || failedObjectLocations.contains(location)) {
                        failedObjectLocations.add(e.key)
                        open = open.remove(e.key)
                        terminated = false
                        break
                    }
                }
            }
        }

        return GraphDefinition(
            ObjectLocationMap(open), graphStructure)
    }


    fun hasErrors(): Boolean {
        return failures.values.isNotEmpty()
    }
}