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


    /**
     * Per-tick stepping budget, shared across the whole frame tree (one control spans the root and
     * every sub-logic). A freshly-reached step (one not already Running — i.e. not on the resume spine)
     * consults this while a [LogicCommand.Pause] is in effect: true means "execute this one step though
     * paused" (and consumes the budget), false means "pause before it" so it shows as the next-to-run
     * boundary. Steps already Running bypass this entirely (they always resume). Returns false by
     * default (no budget), so a fresh step pauses unless a budget was explicitly granted for the tick.
     */
    fun consumeStepBudget(): Boolean = false


    /**
     * While true, a [LogicCommand.Pause] is ignored at fresh-step boundaries so the current sub-tree
     * runs to completion regardless of the budget. Used by Step Over: a RunStep being stepped over wraps
     * its child execution in [pushSuppressPause]/[popSuppressPause] so the nested sub-document runs free,
     * then the parent pauses at its next step. Nestable. Returns false by default.
     */
    fun suppressPause(): Boolean = false


    /**
     * True for the duration of a single Step-Over tick. A RunStep making a fresh descent on this tick
     * runs its child sub-logic to completion (wrapping it in [pushSuppressPause]/[popSuppressPause])
     * instead of descending into it. False by default.
     */
    fun stepOverActive(): Boolean = false


    // Enter/leave a run-free region (see [suppressPause]); nestable. No-ops by default.
    fun pushSuppressPause() {}
    fun popSuppressPause() {}


    /**
     * Enter/leave a sub-logic frame, maintaining the current frame depth (root = 0). Called by every
     * frame boundary (a Script's RunStep, a Flow's Run-Logic vertex) around the child execution. Used by
     * Step Out (see [inStepOutRegion]). No-ops by default.
     */
    fun enterFrame() {}
    fun exitFrame() {}


    /**
     * True when a Step Out is active and the currently-executing frame is at or below the frame the user
     * stepped out of (i.e. its depth >= the step-out target depth). A fresh step in such a frame runs
     * regardless of the Pause command, so the target frame and its descendants run to completion; once
     * control returns to a shallower (caller) frame this is false again and it pauses at the next step.
     * False by default.
     */
    fun inStepOutRegion(): Boolean = false
}
