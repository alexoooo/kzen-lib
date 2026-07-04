package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.ResourceClosePolicy.Companion.parse


/**
 * What happens to a run-scoped resource (e.g. a spawned process or a browser), and which document's lifetime
 * it is bound to, when that document reaches a terminal state. A notation value declared per-resource on the
 * opening step; the engine decomposes it into a target node ([tech.kzen.lib.common.exec.engine.ResourceScope])
 * plus a disposal rule ([tech.kzen.lib.common.exec.engine.ClosePolicy]) at registration.
 */
enum class ResourceClosePolicy(
    /** Canonical notation wire value; the inverse of [parse]. */
    val key: String
) {
    /** Dispose at this document's completion (success, failure, or cancel). */
    Auto("auto"),

    /** Never auto-dispose; only the explicit closing step disposes (survives a forgotten close). */
    Manual("manual"),

    /** Dispose on this document's success/cancel, but keep on its failure so it can be inspected. */
    KeepOnFailure("keepOnFailure"),

    /** Dispose when the parent document (one level up, the caller) completes — success, failure, or cancel. */
    ParentDocument("parent"),

    /** Dispose on the parent document's success/cancel, but keep if the parent failed so it can be inspected. */
    ParentDocumentKeepOnFailure("parentKeepOnFailure"),

    /** Dispose when the overall run (the root document) completes — success, failure, or cancel. */
    Run("run"),

    /** Dispose on the run's success/cancel, but keep if the run failed so it can be inspected. */
    RunKeepOnFailure("runKeepOnFailure");


    companion object {
        fun parse(value: String): ResourceClosePolicy {
            val normalized = value.lowercase()
            return entries.firstOrNull { it.key.lowercase() == normalized }
                ?: throw IllegalArgumentException(
                    "Unknown closePolicy '$value', expected one of: ${entries.joinToString(", ") { it.key }}")
        }
    }
}
