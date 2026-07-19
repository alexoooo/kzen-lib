package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.location.ObjectLocationMap


/**
 * Creation-side mirror of GraphDefinitionAttempt, minus graphStructure (a [GraphInstance] has never carried
 *  structure - callers that need it already hold the GraphDefinition).
 */
data class GraphInstanceAttempt(
    val objectInstances: GraphInstance,
    val failures: ObjectLocationMap<ObjectCreationFailure>
) {
    fun successful(): GraphInstance {
        return objectInstances
    }


    fun hasErrors(): Boolean {
        return failures.map.isNotEmpty()
    }
}
