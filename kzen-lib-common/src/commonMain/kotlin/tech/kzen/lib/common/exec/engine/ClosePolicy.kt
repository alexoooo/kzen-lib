package tech.kzen.lib.common.exec.engine


/**
 * What happens to a node-scoped resource when its node settles to a terminal state (logic-spec §6).
 * Resource ownership is tree-scoped: a resource registered via [Execution.resource] is disposed when its
 * owning node settles, per this policy. The run-global scope is just the root node.
 */
enum class ClosePolicy {
    /** Dispose on completion (success, failure, or cancel). */
    Auto,

    /** Never auto-dispose; only an explicit closing action disposes it (survives a forgotten close). */
    Manual,

    /** Dispose on success/cancel, but retain on a failed node for inspection. */
    KeepOnFailure
}
