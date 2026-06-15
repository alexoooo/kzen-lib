package tech.kzen.lib.common.exec.logic


/**
 * What happens to a run-scoped resource (e.g. a spawned process or a browser) when its Logic run
 * reaches a terminal state. Declared per-resource on the opening step; consumed by
 * [LogicResourceScope.disposeAll].
 */
enum class ResourceClosePolicy {
    /** Dispose at run completion (success, failure, or cancel). */
    Auto,

    /** Never auto-dispose; only the explicit closing step disposes (survives a forgotten close). */
    Manual,

    /** Dispose on success/cancel, but keep on a failed run so it can be inspected. */
    KeepOnFailure;


    companion object {
        fun parse(value: String): ResourceClosePolicy {
            return when (value.lowercase()) {
                "auto" -> Auto
                "manual" -> Manual
                "keeponfailure" -> KeepOnFailure
                else -> throw IllegalArgumentException(
                    "Unknown closePolicy '$value', expected one of: auto, manual, keepOnFailure")
            }
        }
    }
}
