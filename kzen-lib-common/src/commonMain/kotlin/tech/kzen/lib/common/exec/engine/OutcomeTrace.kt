package tech.kzen.lib.common.exec.engine


/**
 * The canonical wire shape of an [Outcome] for the trace surface — a small `{kind, message, at}` map that
 * round-trips through `ExecutionValue.of(map)` / `.get()` exactly as a Worker's progress map does. Shared by
 * the server projection (RunEngineLogicTrace, which emits it at a node's [outcome path][
 * tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath.nodeOutcome]) and the client (the Job UI's
 * per-Worker outcome chip), so the key names can't drift.
 *
 * [Outcome.Success]'s bindings are deliberately dropped — the chip needs only the kind (and, for a
 * failure, the message + origin [Outcome.Failed.at], carried as its stable-id string for the client to
 * resolve).
 */
object OutcomeTrace {
    const val kindKey = "kind"
    const val messageKey = "message"
    const val atKey = "at"

    const val kindSuccess = "Success"
    const val kindFailed = "Failed"
    const val kindCancelled = "Cancelled"


    fun toMap(outcome: Outcome): Map<String, Any?> {
        return when (outcome) {
            is Outcome.Success ->
                mapOf(kindKey to kindSuccess)

            is Outcome.Failed ->
                mapOf(
                    kindKey to kindFailed,
                    messageKey to outcome.message,
                    atKey to outcome.at?.value)

            Outcome.Cancelled ->
                mapOf(kindKey to kindCancelled)
        }
    }
}
