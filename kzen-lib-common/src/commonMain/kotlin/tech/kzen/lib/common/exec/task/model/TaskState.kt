package tech.kzen.lib.common.exec.task.model


enum class TaskState {
    Running,
    CancelRequested,
    FinishedOrFailed,
    Cancelled
}