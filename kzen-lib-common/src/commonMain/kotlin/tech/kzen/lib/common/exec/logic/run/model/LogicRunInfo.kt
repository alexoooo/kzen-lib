package tech.kzen.lib.common.exec.logic.run.model


data class LogicRunInfo(
    val id: LogicRunId,
    val frame: LogicRunFrameInfo,
    val state: LogicRunState,

    // The run's global monotonic trace high-water (RunState.sequence): every emit / log / park /
    // settle advances it. A consumer that has already projected this sequence has nothing new to
    // fetch, so it doubles as the run's cache version.
    val sequence: Long
) {
    companion object {
        private const val idKey = "id"
        private const val frameKey = "frame"
        private const val stateKey = "state"
        private const val sequenceKey = "sequence"

        fun ofCollection(collection: Map<String, Any>): LogicRunInfo {
            @Suppress("UNCHECKED_CAST")
            val frame = LogicRunFrameInfo.ofCollection(
                collection[frameKey] as Map<String, Any>
            )

            return LogicRunInfo(
                LogicRunId(collection[idKey] as String),
                frame,
                LogicRunState.valueOf(collection[stateKey] as String),
                (collection[sequenceKey] as String).toLong()
            )
        }
    }

    fun toCollection(): Map<String, Any> {
        return mapOf(
            idKey to id.value,
            frameKey to frame.toCollection(),
            stateKey to state.name,
            sequenceKey to sequence.toString()
        )
    }
}
