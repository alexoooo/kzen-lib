package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One node in the immutable run-state tree — a single execution of a Logic. This one structure serves as
 * both the *frame tree* (nested execution display) and the *execution tree* (trace attribution): each node
 * carries its per-invocation [id], its rename-stable [stableId], its [status], its live latest-value map,
 * its [children] (in spawn order), and — for a hosted child — the [callerStableId] of the element that
 * hosted it. It is a value: every published [RunState] is a coherent whole-tree snapshot, safe to read
 * without locking.
 */
data class Node(
    val id: NodeId,
    val stableId: ObjectStableId,
    val status: NodeStatus,
    val live: Map<Address, ExecutionValue>,
    val children: List<Node>,

    // The stable id of the element that hosted this node (the call-site — a RunStep, a Job worker), or null
    // for the run root / a host that named no distinct caller. Part of the execution tree (trace attribution),
    // NOT the frame display: it lets a consumer scope a hosting element's view to the executions it spawned.
    val callerStableId: ObjectStableId? = null,

    // Whether this node's trace buffer is KEPT after the frame closes (§7 retention-vs-bounding). True (the
    // default, and always the root) retains it for post-run review — the film-strip / RunStep screenshot strip
    // shows every finished invocation. False lets a consumer evict this frame's buffer when it settles terminal
    // — the bound a long STREAMING host (one child per element) opts into so its finished per-element frames
    // don't accumulate. Carried from the hosting [Execution.host] call; the engine only records it (the trace
    // store is the consumer that acts on it — see ServerLogicController).
    val retainTrace: Boolean = true
)
