package tech.kzen.lib.common.exec.engine.disposal

import tech.kzen.lib.common.exec.engine.ClosePolicy


/**
 * What happens to an ANONYMOUS frame-scoped disposal ([tech.kzen.lib.common.exec.engine.Execution.onSettle])
 * when its frame settles.
 *
 * Two values where a managed binding has three, and the missing one is not an oversight: `manual` is a
 * promotion — it hands a registration one frame up so something running later can still find it and close it —
 * and an anonymous registration has no name for anything to find it by. Offering it here would be a choice
 * nothing could act on.
 */
enum class SettleDisposalPolicy {
    /** Dispose on every terminal outcome: success, failure, or cancel. */
    Auto,

    /** Dispose on success/cancel; on failure the closer is never invoked, leaving the side effect undone. */
    KeepOnFailure;


    fun toClosePolicy(): ClosePolicy {
        return when (this) {
            Auto -> ClosePolicy.Auto
            KeepOnFailure -> ClosePolicy.KeepOnFailure
        }
    }
}
