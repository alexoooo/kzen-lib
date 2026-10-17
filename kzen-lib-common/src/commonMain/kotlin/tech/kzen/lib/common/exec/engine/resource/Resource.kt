package tech.kzen.lib.common.exec.engine.resource

import kotlinx.coroutines.CompletableDeferred
import tech.kzen.lib.platform.platformSynchronized


/**
 * One thing that must be released, counted by holder: the [native] it stands for (null for an anonymous
 * teardown) and the [closer] that releases it. The count of holds is the number of live holds across
 * holders; the last release runs the closer exactly once, and a hold taken while another is still held keeps
 * the count from touching zero between holders. Closing is a claim: whichever release (or [detach]) wins
 * closes, every later attempt is a no-op, and a closer that throws still leaves the entry closed — the failure
 * is thrown from the release that closed it. [hold] fails by name once closed. Thread-safe: holds are taken
 * and released from any thread, and never under any lock but this entry's own.
 *
 * A [borrowed] entry has no closer and never closes on its own: it stands for an object somebody else owns,
 * counted here so the live view shows who is using it and nobody in this run closes it.
 */
class Resource(
    val native: Any?,
    private val closer: (() -> Unit)?,
    private val onClosed: ((Resource) -> Unit)? = null
) {
    companion object {
        /** An entry for an object this run must never close: counted, visible, never closed by a release. */
        fun borrowed(native: Any): Resource =
            Resource(native, null)
    }


    private val lock = Any()
    // Created on the first hold: a pooled object's trip out of its pool must not cost a map it may never fill
    private var holds: LinkedHashMap<Holder, Int>? = null
    private var total = 0
    private var closeClaimed = false
    private var closedFlag = false
    // Created by the first waiter, completed by the close; guarded by [lock] against the close racing the wait
    private var closedSignal: CompletableDeferred<Unit>? = null


    val isClosed: Boolean
        get() = platformSynchronized(lock) { closedFlag }

    /** Whether this entry stands for something nobody in the run owns (see [borrowed]). */
    val isBorrowed: Boolean
        get() = closer == null


    /** Live holds by holder (a snapshot). */
    fun holds(): Map<Holder, Int> =
        platformSynchronized(lock) { holds?.let { LinkedHashMap(it) } ?: emptyMap() }


    fun holdCount(): Int =
        platformSynchronized(lock) { total }


    /** Suspends until the entry is closed (the last release, or a detach); returns at once when it already is. */
    suspend fun awaitClosed() {
        val signal = platformSynchronized(lock) {
            if (closedFlag) null
            else closedSignal ?: CompletableDeferred<Unit>().also { closedSignal = it }
        }
        signal?.await()
    }


    /** Takes one hold for [holder]; fails by name once the entry is closed. */
    fun hold(holder: Holder): Hold {
        platformSynchronized(lock) {
            check(!closedFlag) {
                "$this is already closed; no hold can be taken"
            }
            val counts = holds ?: LinkedHashMap<Holder, Int>(4).also { holds = it }
            counts[holder] = (counts[holder] ?: 0) + 1
            total++
        }
        return Entry(holder)
    }


    /**
     * Closed WITHOUT running the closer: whoever calls this declares the native already released by other means.
     * Holders' later releases are no-ops and their reads fail by name, exactly as after an ordinary close.
     */
    fun detach() {
        if (!claimClose()) {
            return
        }
        onClosed?.invoke(this)
    }


    private fun releaseHold(holder: Holder) {
        val last = platformSynchronized(lock) {
            val counts = holds
            val current = counts?.get(holder)
            if (current == null) false
            else {
                if (current == 1) counts.remove(holder) else counts[holder] = current - 1
                total--
                total == 0 && !closedFlag && closer != null
            }
        }
        if (last) {
            closeOnce()?.let { throw it }
        }
    }


    // Wins the single close: true for the one caller that claimed it, after which the entry reads closed
    private fun claimClose(): Boolean {
        val claimed = platformSynchronized(lock) {
            if (closeClaimed) null
            else {
                closeClaimed = true
                closedFlag = true
                holds = null
                total = 0
                true
            }
        }
        if (claimed == null) {
            return false
        }
        // Only a waiter that already exists is woken: a close nobody awaits allocates nothing
        platformSynchronized(lock) { closedSignal }?.complete(Unit)
        return true
    }


    private fun closeOnce(): Throwable? {
        if (!claimClose()) {
            return null
        }
        val failure = try {
            closer?.invoke()
            null
        }
        catch (e: Throwable) {
            e
        }
        onClosed?.invoke(this)
        return failure
    }


    private inner class Entry(
        private val holder: Holder
    ): Hold {
        private var released = false

        override val isActive: Boolean
            get() = platformSynchronized(lock) { !released }

        override fun release() {
            val first = platformSynchronized(lock) {
                if (released) false
                else {
                    released = true
                    true
                }
            }
            if (first) {
                releaseHold(holder)
            }
        }
    }


    override fun toString(): String {
        val subject = native?.let { it::class.simpleName ?: "native" } ?: "anonymous"
        return "Resource($subject, holds=${holds()}, closed=$isClosed)"
    }
}
