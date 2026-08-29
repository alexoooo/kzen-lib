package tech.kzen.lib.common.exec.logic.model

import tech.kzen.lib.common.exec.data.binding.BindingSchema


data class LogicDefinition(
    val inputs: BindingSchema,
    val outputs: BindingSchema
)
