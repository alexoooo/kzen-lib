package tech.kzen.lib.common.exec.logic.model

import tech.kzen.lib.common.exec.tuple.TupleDefinition


data class LogicDefinition(
    val inputs: TupleDefinition,
    val outputs: TupleDefinition,
//    val canPause: Boolean
)
