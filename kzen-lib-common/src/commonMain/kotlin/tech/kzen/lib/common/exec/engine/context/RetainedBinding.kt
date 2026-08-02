package tech.kzen.lib.common.exec.engine.context

import tech.kzen.lib.common.exec.engine.NodeId


/**
 * A managed binding a settled frame KEPT instead of disposing: a `manual` binding at the root (logic-spec §6's
 * forgotten close) or a `keepOnFailure` binding on a frame that failed, held for inspection.
 *
 * Retention only means something if what was retained can still be found and closed. Without a record of it
 * the registry entry would simply disappear at settle while the browser or process it names kept running —
 * which is a leak wearing the word "retain", not inspection.
 */
data class RetainedBinding(
    /** The frame it rested on when that frame settled. */
    val node: NodeId,

    val key: ContextKey,

    /** The live handle the binding stored, if any. */
    val value: Any?
)
