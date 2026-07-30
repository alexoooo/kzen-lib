package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * A repositioning request addressed to ONE frame of the execution tree (logic-spec §4 "Repositioning"):
 * [target] is the element that frame's walk moves to, and [callSitePath] says WHICH frame — root → the
 * addressed frame, one entry per hop, each the [Execution.host] `callerStableId` of that hop. An empty path
 * addresses the root frame.
 *
 * The path is what makes the request unambiguous under recursion: a self-hosting document has the same
 * [ObjectStableId] live in several frames at once, so a target id on its own resolves in every one of them
 * and would move them all. The engine delivers [target] to the addressed frame alone (as
 * [Execution.moveTarget]) and hands each frame on the way there a descent obligation instead
 * ([Execution.moveDescendCallSite]).
 *
 * Bundling the two in one nullable value makes "a path with no target" unrepresentable: a migration barrier
 * carries a whole addressed request or nothing.
 */
data class MoveTarget(
    val target: ObjectStableId,
    val callSitePath: List<ObjectStableId> = emptyList()
)
