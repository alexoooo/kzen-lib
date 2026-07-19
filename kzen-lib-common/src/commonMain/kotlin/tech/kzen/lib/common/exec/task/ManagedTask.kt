package tech.kzen.lib.common.exec.task

import tech.kzen.lib.common.exec.ExecutionRequest


/**
 * Long-running Task-paradigm entry point: [start] launches the work and reports through [TaskHandle]
 * (returning a [TaskRun] for cancellable work, or null if it completed inline).
 *
 * Statelessness contract: a ManagedTask is instantiated from notation, and the hosting runtime may
 * cache and reuse one instance across submissions - including concurrent ones. Instance fields must
 * be immutable configuration (notation-derived values, injected services); all per-run state belongs
 * in the [TaskRun] / locals created by [start].
 */
interface ManagedTask {
    suspend fun start(
        request: ExecutionRequest,
        handle: TaskHandle
    ): TaskRun?
}