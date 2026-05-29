package tech.kzen.lib.common.exec.logic.trace.model


data class LogicTraceQuery(
    val prefix: LogicTracePath
) {
    companion object {
        fun parse(asString: String): LogicTraceQuery {
            val prefix = LogicTracePath.parse(asString)
            return LogicTraceQuery(prefix)
        }
    }


    fun match(logicTracePath: LogicTracePath): Boolean {
        return logicTracePath.startsWith(prefix)
    }


    fun asString(): String {
        return prefix.asString()
    }
}
