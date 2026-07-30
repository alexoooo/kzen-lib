package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Implemented by a [Logic] that can take part in a [MoveTarget] request carried across the live-edit
 * migration barrier (logic-spec §5), in either of the two roles that request defines (logic-spec §4
 * "Repositioning"):
 *
 * - as the **addressed** frame it reads [Execution.moveTarget] and repositions its own run walk to that
 *   element at restore time — a self-migration with state surgery;
 * - as a **transit** frame on the call-site path to the addressed frame it reads
 *   [Execution.moveDescendCallSite] and honours the descent obligation: run to that call-site with its own
 *   boundary suppressed, then host it.
 *
 * Both members are **static structural** checks against this Logic's own structure — NOT reachability
 * guarantees (a re-evaluated branch condition may still send the walk elsewhere). A driver resolves the
 * request's [call-site path][MoveTarget.callSitePath] to the Logic of each frame along it and checks every one
 * before rebuilding: each transit frame must answer [canDescendThrough] for the hop it carries, and the
 * addressed frame must answer [canMoveTo] for the target. A move no frame on the path can honour is rejected,
 * so the migration barrier is never torn down for a move it cannot carry out. A Logic that does not implement
 * this interface can be neither an addressed frame nor a transit hop: it ignores both surfaces and rebuilds as
 * an ordinary migrate parked at its existing frontier.
 *
 * Asking each frame about its own role is what keeps the driver flavour-agnostic: only the Logic knows which
 * of its elements can carry a descent (a Script rejects a call-site inside a loop body, whose walk cannot be
 * resumed mid-iteration), so the driver never reasons about any flavour's structure.
 */
interface Repositionable {
    /** The [target] resolves to a legal move-to element in this Logic's structure. */
    fun canMoveTo(target: ObjectStableId): Boolean


    /**
     * [callSite] resolves to an element of this Logic's structure that the run walk can reach with its own
     * boundary suppressed, so a paused rebuild descends through it into the frame it hosts rather than parking
     * there. False for an element this Logic does not own, one that is never walked, or one whose position the
     * rebuild cannot re-establish.
     */
    fun canDescendThrough(callSite: ObjectStableId): Boolean
}
