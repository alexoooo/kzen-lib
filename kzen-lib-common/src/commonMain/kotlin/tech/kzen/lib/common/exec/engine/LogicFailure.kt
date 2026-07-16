package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Thrown by [Logic.run] to signal a *recoverable* failure — the engine settles the node to
 * [Outcome.Failed] (or, when pause-on-error is enabled, pauses it for fix-and-resume).
 *
 * Any other (non-cancellation) throwable escaping [Logic.run] is also treated as a failure; [LogicFailure]
 * is the explicit, message-carrying form a Logic raises deliberately.
 *
 * [at] optionally names the element the failure originated at ([Outcome.Failed.at]). The engine re-throws a
 * hosted child's [Outcome.Failed] as a [LogicFailure] carrying the child's [at], so the origin survives the
 * flatten up the host chain; null lets the engine stamp the raising node's own id.
 */
class LogicFailure(
    message: String,
    val at: ObjectStableId? = null
): RuntimeException(message)
