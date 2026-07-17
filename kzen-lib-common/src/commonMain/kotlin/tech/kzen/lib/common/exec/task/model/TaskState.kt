package tech.kzen.lib.common.exec.task.model

import kotlinx.serialization.Serializable


// SER4: @Serializable so TaskModel can embed it; kotlinx encodes an enum by name = the old `state.name`.
@Serializable
enum class TaskState {
    Running,
    CancelRequested,
    FinishedOrFailed,
    Cancelled
}