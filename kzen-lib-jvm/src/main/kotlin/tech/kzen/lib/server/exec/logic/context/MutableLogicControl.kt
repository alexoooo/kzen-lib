package tech.kzen.lib.server.exec.logic.context

import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


/**
 * The stepping model is two per-spine primitives plus the run command:
 *
 * - [stepBudget] — granted per Step tick, consumed by the first fresh boundary reached ([consumeStepBudget]).
 * - [depthLimit] — a fresh boundary DEEPER than this runs free ([runningFreeByDepth]); together with the
 *   budget this expresses Step Into ([arm] budget 1, limit MAX), Step Over (budget 1, limit = stepped depth)
 *   and Step Out (budget 0, limit = caller depth). A plain pause is budget 0, limit MAX.
 *
 * All step state is per-control: a Script / Flow run uses one control across its frame tree, while a Job
 * gives each concurrently-hosted child its own control, so concurrent spines never corrupt one another's
 * [frameDepth] / budget. Only the run COMMAND is shared — a child control delegates [pollCommand] to its
 * host via [commandSource], so a pause / cancel reaches every child instantly with no broadcast race.
 */
class MutableLogicControl(
    private val pauseOnError: Boolean = false,

    // When set, [pollCommand] delegates here instead of reading this control's own [command]: a Job child
    // control sources the run command from the shared host control while keeping its own step state.
    private val commandSource: (() -> LogicCommand)? = null
):
    LogicControl,
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    private val command = AtomicReference(LogicCommand.None)
    private val requestSubscriber = AtomicReference<(ExecutionRequest) -> ExecutionResult>()

    // Stepping budget for this spine. Granted per Step tick, consumed by the first fresh boundary reached.
    private val stepBudget = AtomicInteger(0)

    // Current frame depth (root = 0), maintained by every frame boundary via enterFrame/exitFrame.
    private val frameDepth = AtomicInteger(0)

    // A fresh boundary deeper than this runs free regardless of pause/budget (MAX = unbounded). Set per
    // Step tick: MAX for plain pause / Step Into, the stepped depth for Step Over, caller depth for Step Out.
    @Volatile
    private var depthLimit: Int = Int.MAX_VALUE


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


    // Arm this spine for the next tick: a budget (0 or 1) of fresh boundaries to run, and a depth limit
    // beyond which boundaries run free. Called by the controller before submitting the execution:
    //   resume / pause -> arm(0)        step into -> arm(1)
    //   step over -> arm(1, steppedDepth)        step out -> arm(0, callerDepth)
    // The Job host arms each child with arm(1) per wavefront (descend one boundary into each child).
    fun arm(budget: Int, depthLimit: Int = Int.MAX_VALUE) {
        stepBudget.set(budget)
        this.depthLimit = depthLimit
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
    override fun pollCommand(): LogicCommand {
        // A locally-set Cancel always wins (so cancelAll can abort a child whose host control is NOT cancelled —
        // a migrate / deadlock teardown); otherwise a child delegates the run command to its host.
        val own = command.get()
        if (own == LogicCommand.Cancel || commandSource == null) {
            return own
        }
        return commandSource.invoke()
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


    override fun runningFreeByDepth(): Boolean {
        return frameDepth.get() > depthLimit
    }


    override fun armedStepBudget(): Int {
        return stepBudget.get()
    }


    override fun armedDepthLimit(): Int {
        return depthLimit
    }


    override fun enterFrame() {
        frameDepth.incrementAndGet()
    }


    override fun exitFrame() {
        frameDepth.decrementAndGet()
    }


    override fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult) {
        val wasSet = requestSubscriber.compareAndSet(null, subscriber)
        check(wasSet) { "Already subscribed" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
    }
}
