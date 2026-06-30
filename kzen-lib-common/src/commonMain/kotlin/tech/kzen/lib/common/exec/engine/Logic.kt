package tech.kzen.lib.common.exec.engine

import tech.kzen.lib.common.exec.tuple.TupleValue


/**
 * The single abstraction for general-purpose interactive computation (logic-spec §"What a Logic is").
 *
 * A Logic is a suspendable coroutine: [run] executes the *whole* computation on its own coroutine,
 * cooperating with the engine only through the [Execution] it is handed. Position lives on the coroutine
 * stack — a sequence is sequential statements, a loop is a `for`, a branch is an `if` — each settling at a
 * boundary by calling [Execution.checkpoint]. There is no re-entrant "continue-or-start" and no manual
 * position persistence; the engine owns the execution tree and drives pause/step/cancel centrally.
 *
 * Outcomes:
 * - **success** — return the output [TupleValue].
 * - **failed** — throw [LogicFailure] (recoverable; surfaced as [Outcome.Failed]). Any other throwable is
 *   also treated as a failure.
 * - **cancelled** — cooperative: a [Execution.checkpoint] throws [kotlin.coroutines.cancellation.CancellationException]
 *   when the run is cancelled; let it propagate.
 * - **paused** — not a return value: a paused computation is *suspended* at a checkpoint (the engine decides
 *   when to release it), with the reason surfaced in the run snapshot.
 *
 * Concurrency is opt-in: a Logic may launch parallel sub-executions with ordinary structured concurrency
 * (`coroutineScope { launch { host(...) } ... }`); each child is confined (its own node, trace scope, and
 * resource scope) and the engine brings them to a quiescent wavefront for coherent pause/step/edit.
 */
interface Logic {
    fun signature(): LogicSignature

    suspend fun run(execution: Execution): TupleValue
}
