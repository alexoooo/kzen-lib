package tech.kzen.lib.common.exec.task.model


// SER4: VALUE-TREE only (not @Serializable). Derived from a TaskModel's ExecutionSuccess detail (itself on the
// SER2 ExecutionValue plane) and never rides the direct wire; `value` is an opaque Any, so keep the passthrough
// toCollection/fromCollection codec.
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