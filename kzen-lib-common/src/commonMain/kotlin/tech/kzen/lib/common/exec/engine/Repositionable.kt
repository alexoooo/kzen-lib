package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Implemented by a [Logic] whose run walk can be repositioned to a named element, carried across the
 * live-edit migration barrier (logic-spec §5) as [Execution.moveTarget] and interpreted by the flavour at
 * restore time (a self-migration with state surgery — logic-spec §4 "Repositioning").
 *
 * [canMoveTo] is a **static structural** check — the target resolves to a legal move-to element in this
 * Logic's structure — NOT a reachability guarantee (a re-evaluated branch condition may still send the walk
 * elsewhere). A driver checks `newLogic is Repositionable && newLogic.canMoveTo(target)` before rebuilding
 * and rejects an unsupported target, so the migration barrier is never torn down for a move it cannot honour.
 * A Logic that does not implement this interface is not repositionable: it ignores any [Execution.moveTarget]
 * and rebuilds as an ordinary migrate parked at its existing frontier.
 */
interface Repositionable {
    fun canMoveTo(target: ObjectStableId): Boolean
}
