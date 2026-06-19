package tech.kzen.lib.common.exec.logic.run

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicStatus
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation


interface LogicController
    : AutoCloseable
{
    fun status(): LogicStatus


    fun start(
        root: ObjectLocation,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunId?


    fun request(
        runId: LogicRunId,
        executionId: LogicExecutionId,
        request: ExecutionRequest
    ): ExecutionResult


    fun cancel(runId: LogicRunId): LogicRunResponse


    fun pause(runId: LogicRunId): LogicRunResponse


    fun continueOrStart(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse


    fun step(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse


    /**
     * Like [step], but runs any sub-document (RunStep child logic) entered on this tick to completion
     * instead of descending into it — pausing at the next step of the current frame. Default delegates
     * to [step] so controllers that don't support step-over degrade to a normal step.
     */
    fun stepOver(
        runId: LogicRunId,
        snapshotGraphDefinitionAttempt: GraphDefinitionAttempt? = null
    ): LogicRunResponse = step(runId, snapshotGraphDefinitionAttempt)
}
