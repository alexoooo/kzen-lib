package tech.kzen.lib.common.exec.engine.context

import tech.kzen.lib.common.exec.engine.NodeId


/**
 * A hold a settled frame KEPT instead of releasing: a `manual` binding at the root (logic-spec §6's forgotten
 * close), a `manual` binding whose parent already had its key, or a `keepOnFailure` registration — named or
 * anonymous — on a frame that failed, held for inspection.
 *
 * Retention only means something if what was retained can still be found and closed. Without a record of it
 * the hold would simply vanish at settle while the browser or process it keeps open kept running — a leak
 * wearing the word "retain", not inspection — and under a hold count a vanished hold would also keep the
 * resource in the live view forever and stop a co-holder's last release from closing it.
 */
data class RetainedBinding(
    /** The frame it rested on when that frame settled. */
    val node: NodeId,

    /** The binding's key; null for an anonymous `keepOnFailure` registration. */
    val key: ContextKey?,

    /** The live handle the binding stored, if any. */
    val value: Any?
)
