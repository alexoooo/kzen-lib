package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.logic.model.LogicCommand


interface LogicControl {
    fun pollCommand(): LogicCommand


    fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult)


    /**
     * Run-level option: when true, a recoverable step failure
     * pauses the run (so it can be fixed and re-run) instead of ending it.
     */
    fun pauseOnError(): Boolean = false


    /**
     * Per-spine stepping budget. A freshly-reached boundary (one not already Running — i.e. not on the
     * resume spine) consults this while a [LogicCommand.Pause] is in effect AND it is not already running
     * free by depth (see [runningFreeByDepth]): true means "execute this one boundary though paused" (and
     * consumes the budget), false means "pause before it" so it shows as the next-to-run boundary. Steps
     * already Running bypass this entirely (they always resume). Returns false by default (no budget), so a
     * fresh boundary pauses unless a budget was explicitly granted for the tick.
     *
     * Each control owns its own budget (a Script/Flow run has one control across its frame tree; a Job
     * gives each concurrent child its own control), so concurrent spines never share or corrupt it.
     */
    fun consumeStepBudget(): Boolean = false


    /**
     * True when the currently-executing frame is DEEPER than the step's depth limit, so a fresh boundary
     * runs free regardless of the [LogicCommand.Pause] / budget. This is the single depth comparison that
     * subsumes both Step Over (limit = the depth being stepped over, so the child sub-tree it enters runs
     * free) and Step Out (limit = caller depth, so the current frame and its descendants run free until
     * control returns to the caller). With the limit unbounded (a plain pause or Step Into) it is always
     * false. Returns false by default.
     */
    fun runningFreeByDepth(): Boolean = false


    /**
     * Enter/leave a sub-logic frame, maintaining the current frame depth (root = 0). Called by every
     * frame boundary (a Script's RunStep, a Flow's Run-Logic vertex) around the child execution. The depth
     * feeds [runningFreeByDepth]. No-ops by default.
     */
    fun enterFrame() {}
    fun exitFrame() {}


    /**
     * The step budget currently armed for the next tick (the value [consumeStepBudget] will draw from), read
     * by a Job host to mirror the controller's step plan onto each confined child control — so Step Over /
     * Step Out cross the Job boundary, not just Step Into. Returns 0 by default (no step armed).
     */
    fun armedStepBudget(): Int = 0


    /**
     * The depth limit currently armed for the next tick (the bound [runningFreeByDepth] compares against),
     * read alongside [armedStepBudget] by a Job host to propagate the step plan to its children. Returns
     * unbounded ([Int.MAX_VALUE]) by default (no depth-bounded step armed).
     */
    fun armedDepthLimit(): Int = Int.MAX_VALUE
}
