package tech.kzen.lib.common.exec.logic.run.model


/**
 * Identifies a top-level run.
 *
 * Today at most one top-level run is active at any given time — but that is a **current
 * `ServerLogicController` limitation**, not an engine invariant: a `RunEngine` owns one run with no
 * process-global state, so multiple runs may execute concurrently once the controller is made per-run
 * (engine plan E6 "multiple concurrent runs" — deferred). Treat this id as a first-class addressing
 * key, not an assumption that only one run exists.
 */
data class LogicRunId(
    val value: String
) {
    override fun toString(): String {
        return value
    }
}
