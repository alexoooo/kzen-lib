package tech.kzen.lib.common.exec.logic


/**
 * Run-scoped registry of resource closers, disposed when the Logic run reaches a terminal state.
 * An opening step registers an idempotent closer under a domain key; the matching closing step
 * deregisters it after disposing the resource itself, so the auto-closer never double-fires.
 */
interface LogicResourceScope {
    fun register(key: String, closePolicy: ResourceClosePolicy, closer: () -> Unit)

    fun deregister(key: String)

    fun disposeAll(error: Boolean)
}
