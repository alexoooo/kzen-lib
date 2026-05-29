package tech.kzen.lib.common.exec.logic.model

import tech.kzen.lib.common.exec.tuple.TupleValue


sealed class LogicResult {
    abstract fun isTerminal(): Boolean
}


data object LogicResultPaused: LogicResult() {
    override fun isTerminal() = false
}


data object LogicResultCancelled: LogicResult() {
    override fun isTerminal() = true
}


data class LogicResultFailed(
    val message: String
): LogicResult() {
    override fun isTerminal() = true
}


data class LogicResultSuccess(
    val value: TupleValue
): LogicResult() {
    companion object {
        val empty = LogicResultSuccess(TupleValue.empty)
    }

    override fun isTerminal() = true
}
