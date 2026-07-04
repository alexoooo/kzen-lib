package tech.kzen.lib.common.exec.engine


/**
 * Which node in the execution tree owns a resource's lifecycle — i.e. on which node's settle the resource is
 * disposed (per its [ClosePolicy]). Composes with [ClosePolicy]: [ResourceScope] chooses *which* node, the
 * policy chooses *when* that node's settle disposes it.
 *
 * Resource ownership is tree-scoped: a resource registered via [Execution.resource] is normally owned by the
 * node that opened it ([Self]), but an opening element can hand ownership up the tree so the resource outlives
 * its own document — living as long as the calling document ([Parent]) or the whole run ([Root]).
 */
enum class ResourceScope {
    /** Owned by the node that opened it — disposed when that Logic document settles. */
    Self,

    /**
     * Owned by the parent node — disposed when the calling document (the one that [Execution.host]ed this node,
     * one level up) settles. Falls back to [Self] when the opening node is the root (no parent — "if there is one").
     */
    Parent,

    /** Owned by the root node — disposed when the overall run (the root Logic document) settles. */
    Root
}
