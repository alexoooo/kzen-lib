package tech.kzen.lib.common.exec.logic.trace.model

import tech.kzen.lib.common.exec.ExecutionValue
import kotlin.time.Instant


// A single trace write: the traced value plus when it was recorded (server wall clock) and a
// process-global monotonic sequence. The sequence gives a total order across every run/execution
// buffer (the run-scoped merge's latest-wins tiebreaker); the Instant is the human-facing "when".
//
// SER4: VALUE-TREE only (not @Serializable). Rides the ExecutionValue plane via LogicTraceSnapshot.asCollection
// -> ExecutionValue -> SER2's serializer; keep the toCollection/ofCollection codec (its `sequence` stays a
// String, matching LongExecutionValue, to dodge JS/JSON Long precision).
data class LogicTraceEntry(
    val value: ExecutionValue,
    val time: Instant,
    val sequence: Long
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val valueKey = "value"
        private const val timeKey = "time"
        private const val sequenceKey = "sequence"


        fun ofCollection(collection: Map<String, Any>): LogicTraceEntry {
            @Suppress("UNCHECKED_CAST")
            return LogicTraceEntry(
                ExecutionValue.fromJsonCollection(collection[valueKey] as Map<String, Any>),
                Instant.parse(collection[timeKey] as String),
                // NB: serialized as a String (matches LongExecutionValue) to dodge JS/JSON precision.
                (collection[sequenceKey] as String).toLong())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any> {
        return mapOf(
            valueKey to value.toJsonCollection(),
            timeKey to time.toString(),
            sequenceKey to sequence.toString())
    }
}
