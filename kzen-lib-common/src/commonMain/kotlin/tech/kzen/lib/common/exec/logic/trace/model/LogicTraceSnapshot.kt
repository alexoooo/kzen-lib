package tech.kzen.lib.common.exec.logic.trace.model

// {LogicTracePath -> LogicTraceEntry}?
data class LogicTraceSnapshot(
    val values: Map<LogicTracePath, LogicTraceEntry>
) {
    companion object {
        fun ofCollection(
            collection: Map<String, Map<String, Any>>
        ): LogicTraceSnapshot {
            val values = collection
                .map { LogicTracePath.parse(it.key) to LogicTraceEntry.ofCollection(it.value) }
                .toMap()

            return LogicTraceSnapshot(values)
        }
    }


    fun asCollection(): Map<String, Map<String, Any>> {
        return values
            .map { it.key.asString() to it.value.toCollection() }
            .toMap()
    }


    fun filter(startsWith: LogicTracePath): LogicTraceSnapshot {
        val filtered = values.filterKeys { i -> i.startsWith(startsWith) }
        return LogicTraceSnapshot(filtered)
    }
}
