package tech.kzen.lib.common.exec.logic.run.model


enum class LogicRunState {
    Running,
    Stepping,

    Pausing,

    // The three settled (non-executing) pause states. Paused is the auto-step loop's own step-boundary
    // settle (it keeps advancing); ExplicitPaused (a Pause step) and ErrorPaused (pause-on-error) are
    // deliberate halts that stop the loop. See LogicPauseReason.
    Paused,
    ExplicitPaused,
    ErrorPaused,

    Cancelling;


    fun isExecuting(): Boolean {
        return this != Paused && this != ExplicitPaused && this != ErrorPaused
    }
}
