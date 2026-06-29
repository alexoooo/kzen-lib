package tech.kzen.lib.common.exec.logic.run.model

import tech.kzen.lib.common.service.store.normal.ObjectStableId


// One execution within a run: its id, the execution that launched it (parent), and the call-site
// object that launched it (the Run step / Run worker / Run-logic vertex). A run's root execution has
// neither. Unlike the trace timeline (keyed by the document that ran), this lets a consumer attribute
// each execution — and therefore its retained trace events — to the specific Run step invocation that
// spawned it, so two Run steps invoking the same sub-script document don't share each other's events.
// Every execution that opens gets a row (even event-less wrapper sub-scripts), so the execution tree
// can be rebuilt in full.
data class LogicRunExecutionInfo(
    val executionId: LogicExecutionId,
    val parentExecutionId: LogicExecutionId?,
    val callerStableId: ObjectStableId?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val executionKey = "execution"
        private const val parentKey = "parent"
        private const val callerKey = "caller"


        fun ofCollection(collection: Map<String, Any>): LogicRunExecutionInfo {
            return LogicRunExecutionInfo(
                LogicExecutionId(collection[executionKey] as String),
                (collection[parentKey] as String?)?.let { LogicExecutionId(it) },
                (collection[callerKey] as String?)?.let { ObjectStableId(it) })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any> {
        // Omit the absent keys rather than encode null — a root execution has no parent / caller, and
        // the JSON transport drops null map values anyway.
        val result = mutableMapOf<String, Any>(
            executionKey to executionId.value)
        if (parentExecutionId != null) {
            result[parentKey] = parentExecutionId.value
        }
        if (callerStableId != null) {
            result[callerKey] = callerStableId.value
        }
        return result
    }
}
