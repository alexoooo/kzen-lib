package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.service.context.GraphDefiner


// TODO: inline GraphDefinition for direct GraphStructure?
data class GraphDefinitionAttempt(
        val successful: GraphDefinition,
        val failures: ObjectLocationMap<ObjectDefinitionFailure>
) {
    fun transitiveSuccessful(): GraphDefinition {
//        if (failures.isEmpty()) {
//            return successful
//        }

        val failedObjectLocations = failures.values.keys.toMutableSet()
        var open = successful.objectDefinitions.values

        var terminated = false
        while (! terminated) {
            terminated = true

            for (e in open) {
                val host = ObjectReferenceHost.ofLocation(e.key)
                for (objectReference in e.value.references()) {
                    if (GraphDefiner.bootstrapObjects.objectInstances.locateOptional(objectReference) != null) {
                        continue
                    }

                    val location = successful.objectDefinitions.locateOptional(objectReference, host)
                    if (location == null || failedObjectLocations.contains(location)) {
                        failedObjectLocations.add(e.key)
                        open = open.remove(e.key)
                        terminated = false
                        break
                    }
                }
            }
        }

        return successful.copy(objectDefinitions = ObjectLocationMap(open))
    }


    fun hasErrors(): Boolean {
        return failures.values.isNotEmpty()
    }
}