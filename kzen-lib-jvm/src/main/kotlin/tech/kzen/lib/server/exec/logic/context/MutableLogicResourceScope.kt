package tech.kzen.lib.server.exec.logic.context

import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy


/**
 * Accessed from the run's execution thread (register/deregister, during step execution) and from
 * controller callers that dispose at teardown (cancel/shutdown) — hence synchronized.
 */
class MutableLogicResourceScope: LogicResourceScope {
    //-----------------------------------------------------------------------------------------------------------------
    private class Registration(
        val closePolicy: ResourceClosePolicy,
        val closer: () -> Unit
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val registrations = LinkedHashMap<String, Registration>()


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    override fun register(key: String, closePolicy: ResourceClosePolicy, closer: () -> Unit) {
        // Re-register moves the key to the end so disposal stays most-recently-opened first.
        registrations.remove(key)
        registrations[key] = Registration(closePolicy, closer)
    }


    @Synchronized
    override fun deregister(key: String) {
        registrations.remove(key)
    }


    @Synchronized
    override fun disposeAll(error: Boolean) {
        val pending = registrations.values.toList().asReversed()
        registrations.clear()

        for (registration in pending) {
            val dispose = when (registration.closePolicy) {
                ResourceClosePolicy.Auto -> true
                ResourceClosePolicy.KeepOnFailure -> !error
                ResourceClosePolicy.Manual -> false
            }
            if (!dispose) {
                continue
            }

            try {
                registration.closer()
            }
            catch (ignored: Throwable) {
                // best-effort teardown: a failing closer must not block the others
            }
        }
    }
}
