package tech.kzen.lib.common.exec.engine


/**
 * Identifies one execution of a [Logic] within a run — one node in the execution tree.
 *
 * The same Logic definition may run many times in a single run (a loop body, a repeated sub-computation,
 * concurrent workers); each invocation gets a fresh [NodeId]. This is the per-invocation identity that
 * lets the trace attribute events to the *specific* invocation, distinct from the rename-stable
 * [tech.kzen.lib.common.service.store.normal.ObjectStableId] that identifies the *definition* element.
 */
data class NodeId(val value: String) {
    override fun toString(): String = value
}
