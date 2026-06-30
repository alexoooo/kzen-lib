package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * One node in the immutable run-state tree — a single execution of a Logic. This one structure serves as
 * both the *frame tree* (nested execution display) and the *execution tree* (trace attribution): each node
 * carries its per-invocation [id], its rename-stable [stableId], its [status], its live latest-value map,
 * and its [children] (in spawn order). It is a value: every published [RunState] is a coherent whole-tree
 * snapshot, safe to read without locking.
 */
data class Node(
    val id: NodeId,
    val stableId: ObjectStableId,
    val status: NodeStatus,
    val live: Map<Address, ExecutionValue>,
    val children: List<Node>
)
