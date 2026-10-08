package tech.kzen.lib.common.exec.data.value.budget

import kotlin.time.Duration
import kotlin.time.TimeSource


/**
 * A wall-clock limit on reading one value, so a slow-to-read value cannot hold up its reader. It bounds
 * traversing the value, not preparing readers for its types: time the reading thread spends in
 * [ReadPreparation] after the budget starts is not charged. Created and checked on the reading thread.
 */
class ReadBudget(
    private val maximum: Duration
) {
    private val started = TimeSource.Monotonic.markNow()
    private val preparedAtStart = ReadPreparation.elapsed()


    fun exhausted(): Boolean =
        started.elapsedNow() - (ReadPreparation.elapsed() - preparedAtStart) > maximum
}
