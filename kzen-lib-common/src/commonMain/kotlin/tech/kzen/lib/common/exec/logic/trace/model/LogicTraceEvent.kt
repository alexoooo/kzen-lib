package tech.kzen.lib.common.exec.logic.trace.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.time.Instant


// One append-only entry in a run's trace history: an arbitrary value a Logic recorded, with where it
// came from (which execution / root object / producing object) and when (global monotonic sequence +
// wall clock). The timeline is value-agnostic — a screenshot is just a BinaryExecutionValue here;
// nothing in this model knows about screenshots, steps, or scripts.
data class LogicTraceEvent(
    val executionId: LogicExecutionId,
    val rootStableId: ObjectStableId,
    val objectStableId: ObjectStableId,
    val sequence: Long,
    val time: Instant,
    val value: ExecutionValue
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val executionKey = "execution"
        private const val rootKey = "root"
        private const val objectKey = "object"
        private const val sequenceKey = "sequence"
        private const val timeKey = "time"
        private const val valueKey = "value"


        fun ofCollection(collection: Map<String, Any>): LogicTraceEvent {
            @Suppress("UNCHECKED_CAST")
            return LogicTraceEvent(
                LogicExecutionId(collection[executionKey] as String),
                ObjectStableId(collection[rootKey] as String),
                ObjectStableId(collection[objectKey] as String),
                (collection[sequenceKey] as String).toLong(),
                Instant.parse(collection[timeKey] as String),
                ExecutionValue.fromJsonCollection(collection[valueKey] as Map<String, Any>))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any> {
        return mapOf(
            executionKey to executionId.value,
            rootKey to rootStableId.value,
            objectKey to objectStableId.value,
            sequenceKey to sequence.toString(),
            timeKey to time.toString(),
            valueKey to value.toJsonCollection())
    }
}
