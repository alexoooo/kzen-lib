package tech.kzen.lib.common.exec.data.value.budget

import kotlin.time.Duration
import kotlin.time.TimeSource


/** Holds the current thread for [duration]; common code has no sleep, and JS cannot block otherwise. */
internal fun busyWait(duration: Duration) {
    val started = TimeSource.Monotonic.markNow()
    @Suppress("ControlFlowWithEmptyBody")
    while (started.elapsedNow() < duration) {}
}
