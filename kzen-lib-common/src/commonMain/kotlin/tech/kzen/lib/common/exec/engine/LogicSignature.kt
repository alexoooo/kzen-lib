package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.data.binding.BindingSchema


/**
 * A Logic's typed input and output bindings (§3 of logic-spec).
 */
data class LogicSignature(
    val inputs: BindingSchema,
    val outputs: BindingSchema
) {
    companion object {
        val empty = LogicSignature(BindingSchema.empty, BindingSchema.empty)
    }
}
