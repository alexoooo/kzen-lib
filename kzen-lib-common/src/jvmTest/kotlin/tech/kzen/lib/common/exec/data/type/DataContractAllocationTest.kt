package tech.kzen.lib.common.exec.data.type

import com.sun.management.ThreadMXBean
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * A contract-keyed lookup per row (a column projection, a native resolution) compares and hashes contracts that
 * are equal but not always the same object, and allocates nothing for it: measured as this thread's allocated
 * bytes over many comparisons, first hashes and cache hits, the least of a few rounds after warm-up.
 */
class DataContractAllocationTest {
    private val warmupOperations = 20_000
    private val measuredOperations = 50_000
    private val measuredRounds = 3

    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Volatile
    private var sink = 0


    @Test
    fun equalContractsCompareWithoutAllocating() {
        if (!measurable()) return
        for ((shape, make) in shapes) {
            val left = make()
            val right = make()
            assertEquals(left, right, shape)
            val allocated = allocatedBy({}) {
                if (left == right) sink++
            }
            println("WR23 contract equality, $shape: $allocated bytes over $measuredOperations comparisons")
            assertTrue(allocated < measuredOperations, "$shape: $allocated bytes over $measuredOperations comparisons")
        }
    }


    @Test
    fun aContractHashesWithoutAllocating() {
        if (!measurable()) return
        for ((shape, make) in shapes) {
            var fresh = emptyList<DataContract>()
            val allocated = allocatedBy({ count -> fresh = List(count) { make() } }) { index ->
                sink += fresh[index].hashCode()
            }
            println("WR23 contract hash, $shape: $allocated bytes over $measuredOperations first hashes")
            assertTrue(allocated < measuredOperations, "$shape: $allocated bytes over $measuredOperations first hashes")
        }
    }


    @Test
    fun aCacheHitOnAnEqualContractAllocatesNothing() {
        if (!measurable()) return
        for ((shape, make) in shapes) {
            val cached = make()
            val cache = ConcurrentHashMap(mapOf(cached to shape))
            val equal = make()
            val gets = allocatedBy({}) {
                sink += cache[equal]!!.length
            }
            val computes = allocatedBy({}) {
                sink += cache.computeIfAbsent(equal, missing).length
            }
            println("WR23 contract-keyed hits, $shape: $gets / $computes bytes over $measuredOperations gets / computes")
            assertTrue(gets < measuredOperations, "$shape: $gets bytes over $measuredOperations gets")
            assertTrue(computes < measuredOperations, "$shape: $computes bytes over $measuredOperations computes")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val text = DataType.Scalar(ScalarKind.Text)

    // A parsed row: a record payload with its file's metadata one level down
    private fun parsedRow(): DataContract =
        DataContract(DataType.Record(List(8) { DataField(FieldId("column$it"), text) }))
            .withMetadata(MetadataContract(DataType.Record(listOf(
                DataField(FieldId("parent"), DataType.Record(listOf(
                    DataField(FieldId("name"), text),
                    DataField(FieldId("path"), text))))))))

    // A described native record: natives at the root and its fields
    private fun nativeRecord(): DataContract =
        DataContract(
            DataType.Record(listOf(
                DataField(FieldId("id"), text),
                DataField(FieldId("value"), DataType.Scalar(ScalarKind.Floating(64))))),
            mapOf(
                DataTypePath.root to TypeMetadata.of(ClassName("fixture.Reading")),
                DataTypePath(listOf(DataPathSegment.Field(FieldId("id")))) to TypeMetadata.string,
                DataTypePath(listOf(DataPathSegment.Field(FieldId("value")))) to TypeMetadata.double))

    private val missing = Function<DataContract, String> { error("Not cached: $it") }

    private val shapes = listOf<Pair<String, () -> DataContract>>(
        "parsed row" to ::parsedRow,
        "native record" to ::nativeRecord)


    private fun measurable(): Boolean {
        if (!threads.isThreadAllocatedMemorySupported) {
            return false
        }
        threads.isThreadAllocatedMemoryEnabled = true
        return true
    }


    /**
     * The least [operation] allocated in any of [measuredRounds], each round [prepare]d outside the measurement.
     * Inline, so that passing the index boxes nothing.
     */
    private inline fun allocatedBy(prepare: (Int) -> Unit, operation: (Int) -> Unit): Long {
        prepare(warmupOperations)
        repeat(warmupOperations) { operation(it) }
        val thread = Thread.currentThread().threadId()
        return (1..measuredRounds).minOf {
            prepare(measuredOperations)
            val before = threads.getThreadAllocatedBytes(thread)
            repeat(measuredOperations) { operation(it) }
            threads.getThreadAllocatedBytes(thread) - before
        }
    }
}
