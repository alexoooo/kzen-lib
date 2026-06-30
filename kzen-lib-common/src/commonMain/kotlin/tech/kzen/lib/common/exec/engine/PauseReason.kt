package tech.kzen.lib.common.exec.engine


/**
 * Why a checkpoint settled as paused (logic-spec §4 distinct pause reasons). Propagates upward unchanged
 * through nested logic, and interactive clients treat [Boundary] (keep auto-advancing) differently from
 * [Explicit] / [Error] (stop and wait for the user).
 */
enum class PauseReason {
    /** Ordinary step-settle at a boundary. */
    Boundary,

    /** The computation paused itself — a breakpoint / pause-step (see [Execution.pauseHere]). */
    Explicit,

    /** A failure paused the node under pause-on-error, for fix-and-resume. */
    Error
}
