package tech.kzen.lib.common.exec.logic.trace.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue


// A range of one traced list (Execution.emitList): [entries] starts at [offset], and [size] is the whole list's
// size when it was read, so a reader knows how much more there is.
//
// VALUE-TREE only, like LogicTraceEvent: it rides the ExecutionValue plane. The two counts are
// LongExecutionValue (a String on the wire), as elsewhere on that plane.
data class LogicTraceRange(
    val size: Int,
    val offset: Int,
    val entries: List<ExecutionValue>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val sizeKey = "size"
        private const val offsetKey = "offset"
        private const val entriesKey = "entries"


        fun ofExecutionValue(executionValue: ExecutionValue): LogicTraceRange {
            val values = (executionValue as MapExecutionValue).values
            return LogicTraceRange(
                (values.getValue(sizeKey) as LongExecutionValue).value.toInt(),
                (values.getValue(offsetKey) as LongExecutionValue).value.toInt(),
                (values.getValue(entriesKey) as ListExecutionValue).values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            sizeKey to LongExecutionValue(size.toLong()),
            offsetKey to LongExecutionValue(offset.toLong()),
            entriesKey to ListExecutionValue(entries)))
    }
}
