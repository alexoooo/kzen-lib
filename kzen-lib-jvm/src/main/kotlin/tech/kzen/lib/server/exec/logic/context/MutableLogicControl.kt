package tech.kzen.lib.server.exec.logic.context

import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


class MutableLogicControl(
//    private val arguments: TupleValue
    private val pauseOnError: Boolean = false
):
    LogicControl,
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
//    private class RequestPromise(
//        val request: ExecutionRequest,
//        val promise: CompletableFuture<ExecutionResult>
//    )


    //-----------------------------------------------------------------------------------------------------------------
    private val command = AtomicReference(LogicCommand.None)
//    private val requests = ConcurrentLinkedDeque<RequestPromise>()
    private val requestSubscriber = AtomicReference<(ExecutionRequest) -> ExecutionResult>()

    // Stepping budget shared across the frame tree (this single control spans the root + every
    // sub-logic). Granted per Step/Step-Over tick, consumed by the first fresh step reached.
    private val stepBudget = AtomicInteger(0)

    // Nesting depth of run-free regions (Step Over runs a sub-document's child to completion).
    private val suppressPauseDepth = AtomicInteger(0)

    // True for the duration of a single Step-Over tick.
    @Volatile
    private var stepOver: Boolean = false


    //-----------------------------------------------------------------------------------------------------------------
    fun commandCancel(): Boolean {
        val previousCommand = command.getAndSet(LogicCommand.Cancel)
        return previousCommand != LogicCommand.Cancel
    }


    fun commandPause(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Pause)
    }


    fun commandUnpause(): Boolean {
        return command.compareAndSet(LogicCommand.Pause, LogicCommand.None)
    }


    // Grant the per-tick stepping budget (1 for a normal Step / Step Over). Also (re)sets the
    // step-over mode for the tick. Called by the controller before submitting the execution.
    fun grantStepBudget(count: Int, stepOver: Boolean = false) {
        stepBudget.set(count)
        this.stepOver = stepOver
    }


    fun publishRequest(request: ExecutionRequest): ExecutionResult {
        val subscriber = requestSubscriber.get()
            ?: return ExecutionResult.failure("No request listener")

        return try {
            subscriber(request)
        }
        catch (e: Throwable) {
            return ExecutionFailure.ofException(e)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun arguments(): TupleValue {
//        return arguments
//    }


    override fun pollCommand(): LogicCommand {
        return command.get()
    }


    override fun pauseOnError(): Boolean {
        return pauseOnError
    }


    override fun consumeStepBudget(): Boolean {
        while (true) {
            val current = stepBudget.get()
            if (current <= 0) {
                return false
            }
            if (stepBudget.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }


    override fun suppressPause(): Boolean {
        return suppressPauseDepth.get() > 0
    }


    override fun stepOverActive(): Boolean {
        return stepOver
    }


    override fun pushSuppressPause() {
        suppressPauseDepth.incrementAndGet()
    }


    override fun popSuppressPause() {
        suppressPauseDepth.decrementAndGet()
    }


    override fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult) {
        val wasSet = requestSubscriber.compareAndSet(null, subscriber)
        check(wasSet) { "Already subscribed" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
//        while (true) {
//            val next = requests.poll()
//                ?: return
//
//            next.promise.cancel(true)
//        }
    }
}
