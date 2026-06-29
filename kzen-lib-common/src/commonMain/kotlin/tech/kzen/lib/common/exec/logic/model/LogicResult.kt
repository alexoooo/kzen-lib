package tech.kzen.lib.common.exec.logic.model

import tech.kzen.lib.common.exec.tuple.TupleValue


sealed class LogicResult {
    abstract fun isTerminal(): Boolean
}


// Why a run settled into a pause. Lets a client-paced auto-step ("slow motion") loop tell its OWN per-step
// boundary settle (keep advancing) from a deliberate halt that wants the user (stop): a Pause step or a
// pause-on-error. Propagated up unchanged through nested logic, so a Pause step / failure inside a sub-Script,
// Flow or Job surfaces with its real reason.
enum class LogicPauseReason {
    // The run paused at the next step boundary because the stepping budget ran out — the auto-step loop's
    // normal settle between ticks.
    Boundary,

    // A Pause step (an explicit breakpoint) paused the run.
    Explicit,

    // A step failed while pause-on-error was on, so the run paused at it for fix + resume.
    Error
}


data class LogicResultPaused(
    val reason: LogicPauseReason = LogicPauseReason.Boundary
): LogicResult() {
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
