package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.model.LogicCommand


interface LogicControl {
    fun pollCommand(): LogicCommand


    fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult)


    /**
     * Run-level option, fixed for the lifetime of the run: when true, a recoverable step failure
     * pauses the run (so it can be fixed and re-run) instead of ending it.
     */
    fun pauseOnError(): Boolean = false
}
