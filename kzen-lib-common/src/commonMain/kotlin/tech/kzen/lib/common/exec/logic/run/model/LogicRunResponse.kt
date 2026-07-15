package tech.kzen.lib.common.exec.logic.run.model


enum class LogicRunResponse {
    NotFound,
    RunIdMismatch,
    UnableToStart,
    Submitted,

    // The run exists and is settled, but the requested control action could not be honoured and the run was left
    // untouched — e.g. a move-to (Set Next Statement) whose target the current root [Logic] does not support
    // repositioning to (not a [Repositionable], or a structurally-invalid target), or whose recompile failed.
    Rejected
}
