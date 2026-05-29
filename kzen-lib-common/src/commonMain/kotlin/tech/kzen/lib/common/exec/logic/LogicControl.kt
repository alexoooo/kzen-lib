package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.model.LogicCommand


interface LogicControl {
    fun pollCommand(): LogicCommand


    fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult)
}
