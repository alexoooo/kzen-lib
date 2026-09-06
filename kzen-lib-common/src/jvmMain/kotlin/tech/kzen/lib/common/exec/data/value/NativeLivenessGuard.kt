package tech.kzen.lib.common.exec.data.value


/**
 * Consulted by the native value view before it reads through a present native object, so a resource that
 * an owner has already closed fails by name instead of being read silently. The registry itself knows
 * nothing about ownership or runs; a host supplies the guard (the Job ownership ledger's process registry)
 * and the default guard permits everything. Called on every navigation and read, so implementations must be
 * cheap and must return quickly for objects that can never be closed (a scalar, a collection).
 */
fun interface NativeLivenessGuard {
    /** Throws [DataAccessException] when [native] must no longer be read; returns normally otherwise. */
    fun checkLive(native: Any)

    companion object {
        val none = NativeLivenessGuard {}
    }
}
