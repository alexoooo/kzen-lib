package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.locate.ObjectLocationMap


// TODO: inline GraphDefinition for direct GraphStructure?
data class GraphDefinitionAttempt(
        val successful: GraphDefinition,
        val failures: ObjectLocationMap<ObjectDefinitionFailure>
) {
    fun hasErrors(): Boolean {
        return failures.values.isNotEmpty()
    }
}