package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext


/**
 * The load-bearing quiescence primitive of the engine: an N-thread [CoroutineDispatcher] that counts
 * in-flight dispatch tasks. A coroutine suspended on a checkpoint park, awaiting a child, or awaiting a
 * channel contributes zero (its dispatch task has returned to the pool), so `inFlight == 0` is exactly the
 * quiescent wavefront — every spine suspended at a boundary, or done.
 *
 * Correctness note: the engine completes the parking [kotlinx.coroutines.CompletableDeferred]s *while
 * holding its lock*, and kotlinx dispatches the resumed continuations synchronously within `complete()`, so
 * `inFlight` has already been incremented for every released continuation before the engine calls
 * [awaitQuiescent]. There is therefore no "released work not yet counted" race.
 *
 * [onQuiescent] / [onActive] notify the engine of `inFlight` transitions to / from zero (invoked outside the
 * dispatcher lock, so a listener may take the engine lock safely): the engine's deadlock watchdog uses them to
 * detect a run that has gone quiescent while still running without completing (all spines blocked on channels).
 */
class CountingDispatcher(
    threads: Int
): CoroutineDispatcher() {
    private val executor = Executors.newFixedThreadPool(threads.coerceAtLeast(1)) { runnable ->
        Thread(runnable, "kzen-engine").apply { isDaemon = true }
    }

    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var inFlight = 0

    // Set by the engine to observe quiescence transitions (for deadlock detection). Invoked outside [lock].
    @Volatile
    var onQuiescent: (() -> Unit)? = null

    @Volatile
    var onActive: (() -> Unit)? = null


    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val becameActive = lock.withLock {
            inFlight += 1
            inFlight == 1
        }
        if (becameActive) {
            onActive?.invoke()
        }
        executor.execute {
            try {
                block.run()
            }
            finally {
                val becameQuiescent = lock.withLock {
                    inFlight -= 1
                    if (inFlight == 0) {
                        idle.signalAll()
                        true
                    }
                    else {
                        false
                    }
                }
                if (becameQuiescent) {
                    onQuiescent?.invoke()
                }
            }
        }
    }


    /** Block the calling (non-dispatcher) thread until no dispatch task is in flight. */
    fun awaitQuiescent() {
        lock.withLock {
            while (inFlight != 0) {
                idle.await()
            }
        }
    }


    fun close() {
        executor.shutdownNow()
    }
}
