package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.ResourceClosePolicy.Companion.parse


/**
 * What happens to a run-scoped resource (e.g. a spawned process or a browser) when its Logic run
 * reaches a terminal state. A notation value declared per-resource on the opening step, consumed by the
 * run's resource scope at teardown.
 */
enum class ResourceClosePolicy(
    /** Canonical notation wire value; the inverse of [parse]. */
    val key: String
) {
    /** Dispose at run completion (success, failure, or cancel). */
    Auto("auto"),

    /** Never auto-dispose; only the explicit closing step disposes (survives a forgotten close). */
    Manual("manual"),

    /** Dispose on success/cancel, but keep on a failed run so it can be inspected. */
    KeepOnFailure("keepOnFailure");


    companion object {
        fun parse(value: String): ResourceClosePolicy {
            val normalized = value.lowercase()
            return entries.firstOrNull { it.key.lowercase() == normalized }
                ?: throw IllegalArgumentException(
                    "Unknown closePolicy '$value', expected one of: ${entries.joinToString(", ") { it.key }}")
        }
    }
}
