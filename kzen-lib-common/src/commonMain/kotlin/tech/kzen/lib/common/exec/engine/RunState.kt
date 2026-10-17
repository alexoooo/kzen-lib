package tech.kzen.lib.common.exec.engine


/**
 * An immutable snapshot of an entire run: the execution [root] tree plus the high-water [sequence] (the
 * fold index of the last event folded in), and the [resources] open in the run with who holds them. The
 * single-writer engine publishes a fresh [RunState] after each mutation; readers (UI, tests) observe a
 * coherent whole-tree value with no locking.
 */
data class RunState(
    val root: Node,
    val sequence: Long,
    val resources: List<ResourceState> = emptyList()
)
