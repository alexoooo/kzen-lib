package tech.kzen.lib.common.exec.engine.resource

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * A closeable that counts its closes and records the order across instances, so tests can assert exactly-once
 * and close ordering; optionally throws on close.
 */
class CloseCountingResource(
    val name: String,
    private val throwOnClose: Throwable? = null
): AutoCloseable {
    companion object {
        val closeOrder = CopyOnWriteArrayList<String>()

        fun reset() {
            closeOrder.clear()
        }
    }

    private val closes = AtomicInteger()

    val closeCount: Int
        get() = closes.get()

    val isClosed: Boolean
        get() = closes.get() > 0

    override fun close() {
        closes.incrementAndGet()
        closeOrder += name
        throwOnClose?.let { throw it }
    }

    override fun toString(): String = "CloseCountingResource($name, closes=$closeCount)"
}
