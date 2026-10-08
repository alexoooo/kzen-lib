package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.value.budget.ReadPreparation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration


class NativeObjectValueAccessTest {
    // Read by this test only, so their first read in the JVM is the one measured below
    class Holder(val items: List<Leaf>)
    data class Leaf(val label: String)


    @Test
    fun firstReadOfAClassIsPreparationAndALaterReadPreparesNothing() {
        DefaultDataAdapterRegistry().use { registry ->
            val cold = registry.lift(Holder(listOf(Leaf("cold"))))
            val coldPrepared = preparedDuring {
                assertIs<SnapshotResult.Complete>(DataSnapshot.capture(cold))
            }
            assertTrue(coldPrepared > Duration.ZERO, "the first read of Leaf prepares its reader")

            val warmPrepared = preparedDuring {
                val warm = registry.lift(Holder(listOf(Leaf("warm"))))
                assertIs<SnapshotResult.Complete>(DataSnapshot.capture(warm))
            }
            assertEquals(Duration.ZERO, warmPrepared)
        }
    }


    private fun preparedDuring(block: () -> Unit): Duration {
        val before = ReadPreparation.elapsed()
        block()
        return ReadPreparation.elapsed() - before
    }
}
