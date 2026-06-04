package tech.kzen.lib.common.exec.logic.run.model

import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock


/**
 * Same logic can be executed multiple times within each run
 */
data class LogicExecutionId(
    val value: String
) {
    companion object {
        private val clock = Clock.System
        private val random = Random(42)

        @Volatile
        private var previous = clock.now()

        /**
         * Fresh arbitrary ID — timestamp-first (readable and sortable), with a random suffix
         * only on a same-instant collision.
         */
        fun random(): LogicExecutionId {
            val now = clock.now()
            if (now != previous) {
                previous = now
                return LogicExecutionId(now.toString())
            }

            val randomSuffix = random.nextLong()
            return LogicExecutionId("${now}_${randomSuffix.toULong()}")
        }
    }


    override fun toString(): String {
        return value
    }
}
