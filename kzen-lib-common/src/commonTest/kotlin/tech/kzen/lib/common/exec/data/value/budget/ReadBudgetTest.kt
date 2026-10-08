package tech.kzen.lib.common.exec.data.value.budget

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime


class ReadBudgetTest {
    private val budget = 50.milliseconds
    private val overBudget = 150.milliseconds


    @Test
    fun preparationIsNotCharged() {
        val read = ReadBudget(budget)
        ReadPreparation.prepare { busyWait(overBudget) }
        assertFalse(read.exhausted())
    }


    @Test
    fun readingTimeIsCharged() {
        val read = ReadBudget(budget)
        busyWait(overBudget)
        assertTrue(read.exhausted())
    }


    @Test
    fun nestedPreparationIsTimedOnce() {
        val before = ReadPreparation.elapsed()
        val wall = measureTime {
            ReadPreparation.prepare { ReadPreparation.prepare { busyWait(budget) } }
        }
        val prepared = ReadPreparation.elapsed() - before
        assertTrue(prepared in budget..wall, "prepared $prepared within wall $wall")
    }


    @Test
    fun failedPreparationStillEndsItsTiming() {
        assertFailsWith<IllegalStateException> {
            ReadPreparation.prepare { error("preparation failed") }
        }
        val before = ReadPreparation.elapsed()
        ReadPreparation.prepare { busyWait(budget) }
        assertTrue(ReadPreparation.elapsed() - before >= budget)
    }
}
