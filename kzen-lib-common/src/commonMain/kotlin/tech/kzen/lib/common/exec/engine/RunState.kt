package tech.kzen.lib.common.exec.engine


/**
 * An immutable snapshot of an entire run: the execution [root] tree plus the high-water [sequence] (the
 * fold index of the last event folded in). The single-writer engine publishes a fresh [RunState] after
 * each mutation; readers (UI, tests) observe a coherent whole-tree value with no locking.
 */
data class RunState(
    val root: Node,
    val sequence: Long
)
