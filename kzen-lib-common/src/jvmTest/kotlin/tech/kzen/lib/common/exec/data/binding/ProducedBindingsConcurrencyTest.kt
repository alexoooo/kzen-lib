package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ProducedBindingsConcurrencyTest {
    @Test
    fun concurrentYieldsRemainCompleteAndEachNameIsLastWriteWins() {
        val names = (0 until 16).map { BindingName("value$it") }
        val sample = LiteralDataValues.lift(0)
        val schema = BindingSchema.of(names.map { BindingDefinition(it, sample.contract) })
        val builder = ProducedBindingsBuilder(schema)
        val workers = 8
        val rounds = 200
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)

        try {
            repeat(workers) { worker ->
                pool.submit {
                    start.await()
                    repeat(rounds) { round ->
                        val index = (worker + round) % names.size
                        builder.set(names[index], LiteralDataValues.lift(worker * rounds + round))
                    }
                }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

            val settled = builder.settle()
            assertEquals(names, settled.entries().map { it.first.name })
            assertEquals(workers * rounds, builder.yieldChronology().size)
            assertTrue(settled.entries().all { it.second is BindingState.Bound })
        }
        finally {
            pool.shutdownNow()
        }
    }
}
