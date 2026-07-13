package tech.kzen.lib.common.exec.logic.run.model

import tech.kzen.lib.common.model.location.ObjectLocation


data class LogicRunFrameInfo(
    val objectLocation: ObjectLocation,
    val executionId: LogicExecutionId,
//    var state: LogicRunFrameState,
    val dependencies: List<LogicRunFrameInfo>,

    // The frame's current position — the last named boundary its node reached (the element about to run,
    // e.g. the Script next-step highlight), resolved server-side from the node's stable id to the current
    // ObjectLocation; null before the first named boundary (or for a node whose position is itself).
    val position: ObjectLocation? = null,
) {
    companion object {
        private const val locationKey = "location"
        private const val executionKey = "execution"
//        private const val stateKey = "state"
        private const val dependenciesKey = "dependencies"
        private const val positionKey = "position"

        fun ofCollection(collection: Map<String, Any>): LogicRunFrameInfo {
            @Suppress("UNCHECKED_CAST")
            val dependenciesValue = collection[dependenciesKey] as List<Map<String, Any>>

            return LogicRunFrameInfo(
                ObjectLocation.parse(collection[locationKey] as String),
                LogicExecutionId(collection[executionKey] as String),
//                LogicRunFrameState.valueOf(collection[stateKey] as String),
                dependenciesValue.map { ofCollection(it) },
                (collection[positionKey] as String?)?.let { ObjectLocation.parse(it) }
            )
        }
    }


    fun toCollection(): Map<String, Any> {
        return buildMap {
            put(locationKey, objectLocation.asString())
            put(executionKey, executionId.value)
//            put(stateKey, state.name)
            put(dependenciesKey, dependencies.map { it.toCollection() })
            position?.let { put(positionKey, it.asString()) }
        }
    }
}
