package tech.kzen.lib.common.exec.task

import tech.kzen.lib.common.exec.ExecutionRequest


interface ManagedTask {
    suspend fun start(
        request: ExecutionRequest,
        handle: TaskHandle
    ): TaskRun?
}