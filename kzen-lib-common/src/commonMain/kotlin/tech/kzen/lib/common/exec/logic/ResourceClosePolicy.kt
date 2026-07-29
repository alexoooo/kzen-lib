package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy.Companion.parse


/**
 * What happens to a run-scoped resource (e.g. a spawned process or a browser) when the document that OWNS it
 * reaches a terminal state. A notation value declared per-resource on the opening step.
 *
 * The owner is **not** named here: it is the nearest enclosing document declaring a *context slot* for the
 * resource's key ([tech.kzen.lib.common.exec.engine.Execution.declareSlot]), falling back to the opening
 * document when none does. So `auto` means "at the owning slot's settle", which may be several documents up
 * — the ancestor's own declaration, not the opener's unilateral reach-up. This enum is the disposal *rule*
 * only; it maps one-to-one onto the engine's [ClosePolicy] via [toEngine].
 */
enum class ResourceClosePolicy(
    /** Canonical notation wire value; the inverse of [parse]. */
    val key: String
) {
    /** Dispose when the owning document completes (success, failure, or cancel). */
    Auto("auto"),

    /**
     * Never auto-dispose; only the explicit closing step disposes (survives a forgotten close). At the
     * owning document's settle the registration is handed one level up, so it stays readable and
     * releasable by whatever runs after — the second way a resource outlives its opener, without a slot.
     */
    Manual("manual"),

    /** Dispose on the owning document's success/cancel, but keep on its failure so it can be inspected. */
    KeepOnFailure("keepOnFailure");


    fun toEngine(): ClosePolicy {
        return when (this) {
            Auto -> ClosePolicy.Auto
            Manual -> ClosePolicy.Manual
            KeepOnFailure -> ClosePolicy.KeepOnFailure
        }
    }


    companion object {
        fun parse(value: String): ResourceClosePolicy {
            val normalized = value.lowercase()
            return entries.firstOrNull { it.key.lowercase() == normalized }
                ?: throw IllegalArgumentException(
                    "Unknown closePolicy '$value', expected one of: ${entries.joinToString(", ") { it.key }}")
        }
    }
}
