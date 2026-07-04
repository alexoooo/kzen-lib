package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume


/**
 * The load-bearing quiescence primitive of the engine: an N-thread [CoroutineDispatcher] that counts
 * in-flight dispatch tasks. A coroutine suspended on a checkpoint park, awaiting a child, or awaiting a
 * channel contributes zero (its dispatch task has returned to the pool), so `inFlight == 0` is exactly the
 * quiescent wavefront — every spine suspended at a boundary, or done.
 *
 * A coroutine suspended on [delay][kotlinx.coroutines.delay] is the exception: it is *not* parked at a
 * boundary, it is still-running work that will resume on a timer. So this dispatcher also implements [Delay]
 * and counts a pending delay as in-flight — otherwise `delay` (which hands its timer off and frees its
 * dispatch task) would drop `inFlight` to 0 and be misread as quiescence, falsely settling a run mid-step
 * (e.g. a `WaitStep`) into a "paused" state.
 *
 * Correctness note: the engine completes the parking [kotlinx.coroutines.CompletableDeferred]s *while
 * holding its lock*, and kotlinx dispatches the resumed continuations synchronously within `complete()`, so
 * `inFlight` has already been incremented for every released continuation before the engine calls
 * [awaitQuiescent]. There is therefore no "released work not yet counted" race.
 */
@OptIn(InternalCoroutinesApi::class)
class CountingDispatcher(
    threads: Int
): CoroutineDispatcher(), Delay {
    private val executor = Executors.newFixedThreadPool(threads.coerceAtLeast(1)) { runnable ->
        Thread(runnable, "kzen-engine").apply { isDaemon = true }
    }

    // Holds only the delay timers — the resumed continuations still run on [executor]. A single thread is
    // ample: it does no work beyond firing the scheduled resume.
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "kzen-engine-delay").apply { isDaemon = true }
    }

    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var inFlight = 0


    override fun dispatch(context: CoroutineContext, block: Runnable) {
        lock.withLock {
            inFlight += 1
        }
        executor.execute {
            try {
                block.run()
            }
            finally {
                lock.withLock {
                    inFlight -= 1
                    if (inFlight == 0) {
                        idle.signalAll()
                    }
                }
            }
        }
    }


    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        // A delayed continuation is still-running work, not a spine parked at a boundary: count it as
        // in-flight so the run does not read as quiescent while the delay counts down.
        lock.withLock {
            inFlight += 1
        }

        val settled = AtomicBoolean(false)
        val future = scheduler.schedule({
            // resume() re-dispatches the continuation onto this dispatcher (dispatch() bumps inFlight before
            // we release the pending-delay count), so inFlight never transiently hits 0 across the hand-off.
            continuation.resume(Unit)
            release(settled)
        }, timeMillis, TimeUnit.MILLISECONDS)

        continuation.invokeOnCancellation {
            future.cancel(false)
            release(settled)
        }
    }


    // Exactly-once decrement of the pending-delay count: the timer firing and cancellation race, and the CAS
    // guarantees only one of them releases.
    private fun release(settled: AtomicBoolean) {
        if (settled.compareAndSet(false, true)) {
            lock.withLock {
                inFlight -= 1
                if (inFlight == 0) {
                    idle.signalAll()
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
        scheduler.shutdownNow()
    }
}
