package tech.kzen.lib.common.exec.engine.context


/**
 * One ambient binding a caller installs on a child frame at [tech.kzen.lib.common.exec.engine.Execution.host],
 * so the child observes it from its very first instruction.
 *
 * This is the `with(…)` shape: the caller supplies the callee's ambient dependency **per call**, and the callee
 * stays unaware — it declares what it requires and is run twice against two different values without being
 * edited. It is not an ordinary [tech.kzen.lib.common.exec.engine.Execution.bind] the caller could already
 * make: `host` mints the child node and runs it as one operation, and the caller never holds the child's
 * `Execution`, so there is no instant between those two at which it could bind anything.
 *
 * **A borrow, never a handover.** There is deliberately no disposal here — the value's owner is whatever frame
 * bound it on the caller's side, that frame outlives the sequential child by construction, and it disposes as
 * it always would. A `release` inside the callee therefore finds the borrow, unbinds the name, and closes
 * nothing: "stop borrowing", which is what the absence of a disposal already means everywhere else.
 */
data class InitialBinding(
    val key: ContextKey,
    val value: Any?
)
