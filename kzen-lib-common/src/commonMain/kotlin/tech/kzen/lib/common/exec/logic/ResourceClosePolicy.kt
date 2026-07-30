package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy.Companion.parse


/**
 * What happens to a run-scoped resource (e.g. a spawned process or a browser) when the document that OWNS it
 * reaches a terminal state. A notation value declared per-resource on the opening step.
 *
 * The owner is **not** named here: it is the furthest document on the resource's *export chain* — each
 * document in turn exports the resource's key ([tech.kzen.lib.common.exec.engine.Execution.declareExport]),
 * and it rests at the first that does not — falling back to the opening document, which is what an
 * un-exported provide gets. So `auto` means "at the settle of the frame the export chain reached", which may
 * be several documents up, but only as far as an unbroken run of declarations someone wrote. This enum is the
 * disposal *rule* only; it maps one-to-one onto the engine's [ClosePolicy] via [toEngine].
 */
enum class ResourceClosePolicy(
    /** Canonical notation wire value; the inverse of [parse]. */
    val key: String
) {
    /** Dispose when the owning document completes (success, failure, or cancel). */
    Auto("auto"),

    /**
     * Never auto-dispose; only the explicit closing step disposes (survives a forgotten close). At the
     * owning document's settle the registration is handed one level up, so it stays readable and releasable
     * by whatever runs after — the second, orthogonal way a resource outlives its opener, and now the only
     * way an *un-exported* one does.
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
