package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphDefinitionAttempt(
        val successful: GraphDefinition,
        val errors: ObjectLocationMap<String>
) {
    fun hasErrors(): Boolean {
        return errors.values.isNotEmpty()
    }
}