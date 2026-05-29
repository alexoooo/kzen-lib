package tech.kzen.lib.common.exec.task.model


data class TaskProgress(
    val value: Any
) {
    companion object {
        fun fromCollection(value: Any): TaskProgress {
            return TaskProgress(value)
        }
    }


    fun toCollection(): Any {
        return value
    }
}