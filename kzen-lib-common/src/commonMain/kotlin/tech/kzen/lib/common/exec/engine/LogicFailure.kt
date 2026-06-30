package tech.kzen.lib.common.exec.engine


/**
 * Thrown by [Logic.run] to signal a *recoverable* failure — the engine settles the node to
 * [Outcome.Failed] (or, when pause-on-error is enabled, pauses it for fix-and-resume).
 *
 * Any other (non-cancellation) throwable escaping [Logic.run] is also treated as a failure; [LogicFailure]
 * is the explicit, message-carrying form a Logic raises deliberately.
 */
class LogicFailure(
    message: String
): RuntimeException(message)
