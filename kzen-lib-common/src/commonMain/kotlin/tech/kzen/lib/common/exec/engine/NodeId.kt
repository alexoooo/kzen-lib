package tech.kzen.lib.common.exec.engine


/**
 * Identifies one execution of a [Logic] within a run — one node in the execution tree.
 *
 * The same Logic definition may run many times in a single run (a loop body, a repeated sub-computation,
 * concurrent workers); each invocation gets a fresh [NodeId]. This is the per-invocation identity that
 * lets the trace attribute events to the *specific* invocation, distinct from the rename-stable
 * [tech.kzen.lib.common.service.store.normal.ObjectStableId] that identifies the *definition* element.
 *
 * Same identity as the wire-level [tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId] — the
 * run controller (kzen-auto's ServerLogicController) wraps the value 1:1 in both directions. Kept as a
 * separate type so the engine core doesn't depend on the logic-paradigm wire model (nor its
 * timestamp-based minting; the engine mints these from its own per-run counter: "n0", "n1", ...).
 */
data class NodeId(val value: String) {
    override fun toString(): String = value
}
