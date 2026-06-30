package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.tuple.TupleDefinition


/**
 * A Logic's typed signature: named, typed input and output tuples (§3 of logic-spec).
 */
data class LogicSignature(
    val inputs: TupleDefinition,
    val outputs: TupleDefinition
) {
    companion object {
        val empty = LogicSignature(TupleDefinition.empty, TupleDefinition.empty)
    }
}
