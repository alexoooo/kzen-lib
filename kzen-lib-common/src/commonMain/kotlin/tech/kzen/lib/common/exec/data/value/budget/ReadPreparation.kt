package tech.kzen.lib.common.exec.data.value.budget

import tech.kzen.lib.platform.platformThreadLocal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource


/**
 * The one-time setup a value read performs on meeting a type it has not read before (kotlin-reflect loading a
 * native class's members, which on a cold JVM can outlast a whole display budget), timed apart from the read so
 * that a [ReadBudget] does not charge it. A value backing that prepares per-type state lazily, on its first
 * read, does so inside [prepare].
 */
object ReadPreparation {
    private class Tally {
        var depth = 0
        var nanos = 0L
    }

    private val tally = platformThreadLocal { Tally() }


    /** Runs [block] as preparation on the current thread; a nested call is timed as part of the outermost. */
    fun <T> prepare(block: () -> T): T {
        val current = tally()
        val started = TimeSource.Monotonic.markNow()
        current.depth++
        try {
            return block()
        }
        finally {
            current.depth--
            if (current.depth == 0) {
                current.nanos += started.elapsedNow().inWholeNanoseconds
            }
        }
    }


    /** Total time the current thread has spent in [prepare]. */
    fun elapsed(): Duration =
        tally().nanos.nanoseconds
}
