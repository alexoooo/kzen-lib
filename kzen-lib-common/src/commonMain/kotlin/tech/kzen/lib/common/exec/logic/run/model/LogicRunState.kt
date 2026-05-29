package tech.kzen.lib.common.exec.logic.run.model


enum class LogicRunState {
    Running,
    Stepping,

    Pausing,
    Paused,

    Cancelling;


    fun isExecuting(): Boolean {
        return this != Paused
    }
}
