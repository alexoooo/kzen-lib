package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataVariant
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail


/**
 * One value can be read by several threads at once (metadata attached to many values, a run's inputs), so the
 * native view's lazily interned nodes must stay consistent: every reader of one child gets the same node, and the
 * table is never corrupted by concurrent first reads.
 */
class NativeValueAccessConcurrencyTest {
    //-----------------------------------------------------------------------------------------------------------------
    data class Holder(val item: Any)


    /**
     * Once [armed], each reader's first element read waits until every racer is inside one, then the racers go on
     * one at a time in arrival order: a later one resumes only after the one before it [finish]ed its whole read.
     */
    private class Gate {
        @Volatile
        var armed = false
        private val arrived = CountDownLatch(racers)
        private val arrivals = AtomicInteger()
        private val finished = List(racers) { CountDownLatch(1) }
        private val position = ThreadLocal<Int>()

        fun pass() {
            if (!armed || position.get() != null) {
                return
            }
            val arrival = arrivals.getAndIncrement()
            position.set(arrival)
            if (arrival >= racers) {
                return
            }
            arrived.countDown()
            check(arrived.await(gateSeconds, TimeUnit.SECONDS)) { "Concurrent first reads did not all arrive" }
            if (arrival > 0) {
                check(finished[arrival - 1].await(gateSeconds, TimeUnit.SECONDS)) { "An earlier reader did not finish" }
            }
        }

        fun finish() {
            position.get()?.let { finished.getOrNull(it)?.countDown() }
        }
    }


    private class GatedList(private val gate: Gate): AbstractList<String>() {
        override val size: Int
            get() = 1

        override fun get(index: Int): String {
            gate.pass()
            return "item"
        }
    }


    @Test
    fun concurrentFirstReadsOfOneFieldShareOneNode() {
        val gate = Gate()
        DefaultDataAdapterRegistry().use { registry ->
            // Typing the Dynamic field's value iterates the list, so both readers are inside the first read
            val value = registry.lift(Holder(GatedList(gate)))
            assertOneNode(gate) { value.access.field(value.root, FieldId("item")) }
        }
    }


    @Test
    fun concurrentFirstReadsOfOneElementShareOneNode() {
        val gate = Gate()
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift(GatedList(gate))
            assertOneNode(gate) { value.access.element(value.root, 0) }
        }
    }


    @Test
    fun concurrentFirstReadsOfOneEntryShareOneNode() {
        val gate = Gate()
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift(mapOf("key" to GatedList(gate)))
            assertOneNode(gate) { value.access.entry(value.root, TextExecutionValue("key")) }
        }
    }


    @Test
    fun concurrentFirstReadsThroughAUnionShareOneNode() {
        val gate = Gate()
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift(Holder(GatedList(gate)), unionOf(registry.describe(typeOf<Holder>())))
            val selected = value.access.selected(value.root)
            assertOneNode(gate) { value.access.field(selected, FieldId("item")) }
        }
    }


    @Test
    fun concurrentFirstReadsThroughAnOverlayShareOneNode() {
        val gate = Gate()
        DefaultDataAdapterRegistry().use { registry ->
            val base = registry.lift(Holder(GatedList(gate)))
            val value = DataOverlay.record(base, listOf(FieldId("extra") to LiteralDataValues.lift("extra")))
            val item = value.access.field(value.root, FieldId("item"))
            value.access.contract(item)
            assertOneNode(gate) { value.access.element(item, 0) }
        }
    }


    private fun assertOneNode(gate: Gate, read: () -> DataNode) {
        gate.armed = true
        val nodes = inParallel(racers) {
            try {
                read()
            }
            finally {
                gate.finish()
            }
        }
        assertEquals(nodes[0], nodes[1], "concurrent first reads return one node")
        assertEquals(nodes[0], read(), "and later reads return it too")
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Leaf(val label: String, val weight: Double)
    data class Item(
        val id: Long,
        val name: String,
        val tags: List<String>,
        val attributes: Map<String, Int>,
        val leaves: List<Leaf>)
    data class Root(val title: String, val items: List<Item>, val byName: Map<String, Item>)


    @Test
    fun manyReadersOfOneNativeValueReadWhatOneReaderReads() {
        stress { registry -> registry.lift(sample()) }
    }


    @Test
    fun manyReadersOfOneUnionReadWhatOneReaderReads() {
        stress { registry -> registry.lift(sample(), unionOf(registry.describe(typeOf<Root>()))) }
    }


    @Test
    fun manyReadersOfAUnionOverALiteralReadWhatOneReaderReads() {
        // A literal's nodes all exist up front, so only the union's own tokens are minted while reading
        stress { registry ->
            val literal = (DataSnapshot.capture(registry.lift(sample())) as SnapshotResult.Complete)
                .snapshot.asDataValue()
            TaggedUnionValueAccess.value(unionOf(literal.contract), VariantId("native"), literal)
        }
    }


    @Test
    fun manyReadersOfAnOverlayOverANativeReadWhatOneReaderReads() {
        stress { registry ->
            DataOverlay.record(registry.lift(sample()), listOf(FieldId("extra") to LiteralDataValues.lift("extra")))
        }
    }


    /** Every round reads a fresh value from [threads] threads at once, each starting its walk elsewhere. */
    private fun stress(create: (DefaultDataAdapterRegistry) -> DataValue) {
        DefaultDataAdapterRegistry().use { registry ->
            val expected = Walk(create(registry), 0).run().content
            repeat(rounds) { round ->
                val value = create(registry)
                val failures = ConcurrentLinkedQueue<Throwable>()
                val walks = ConcurrentLinkedQueue<Walk>()
                val start = CountDownLatch(1)
                val pool = Executors.newFixedThreadPool(threads) { runnable ->
                    Thread(runnable, "native-value-stress").also { it.isDaemon = true }
                }
                try {
                    repeat(threads) { thread ->
                        pool.submit {
                            start.await()
                            try {
                                walks += Walk(value, thread * rotationStep).run()
                            }
                            catch (e: Throwable) {
                                failures += e
                            }
                        }
                    }
                    start.countDown()
                    pool.shutdown()
                    if (!pool.awaitTermination(stressSeconds, TimeUnit.SECONDS)) {
                        fail("Concurrent reads did not finish within $stressSeconds s (a corrupted map can loop)")
                    }
                }
                finally {
                    pool.shutdownNow()
                }

                failures.firstOrNull()?.let { first ->
                    throw AssertionError("Round $round: ${failures.size} concurrent failure(s); first: $first", first)
                }
                val first = walks.first()
                for (walk in walks) {
                    assertEquals(expected, walk.content, "Round $round: a concurrent reader reads what one reader reads")
                    assertEquals(first.tokens, walk.tokens, "Round $round: every reader gets one node per path")
                }
                assertEquals(first.tokens.size, first.tokens.values.toSet().size,
                    "Round $round: no two paths share a node")
            }
        }
    }


    /** Reads every node of [value], visiting children in an order rotated by [rotation]. */
    private class Walk(private val value: DataValue, private val rotation: Int) {
        val tokens = mutableMapOf<String, Long>()
        val content = mutableMapOf<String, String>()

        fun run(): Walk {
            visit(value.root, "")
            return this
        }

        private fun visit(node: DataNode, path: String) {
            val access = value.access
            tokens[path] = node.token
            val state = access.state(node)
            val type = access.contract(node).structural
            content[path] = "$state ${type::class.simpleName}"
            if (state != DataState.Present) {
                return
            }
            when (type) {
                is DataType.Record -> rotated(type.fields.size) { index ->
                    val id = type.fields[index].id
                    visit(access.field(node, id), "$path.$id")
                }
                is DataType.Listing -> rotated(access.size(node)) { index ->
                    visit(access.element(node, index), "$path[$index]")
                }
                is DataType.Mapping -> rotated(access.size(node)) { index ->
                    val key = access.keyAt(node, index)
                    visit(access.entry(node, key), "$path{$key}")
                }
                is DataType.Union ->
                    visit(access.selected(node), "$path|${access.activeVariant(node)}")
                is DataType.Scalar ->
                    content[path] = "$state ${access.scalar(node)}"
                else -> {}
            }
        }

        private inline fun rotated(size: Int, action: (Int) -> Unit) {
            for (step in 0 until size) {
                action((step + rotation) % size)
            }
        }
    }


    // No object is reached twice, which a snapshot would refuse
    private fun sample(): Root =
        Root("sample", items(), items().associateBy { it.name })


    private fun items(): List<Item> =
        (0 until itemCount).map { index ->
            Item(
                index.toLong(),
                "item-$index",
                (0 until 4).map { "tag-$index-$it" },
                (0 until 3).associate { "attribute-$it" to index * it },
                (0 until 3).map { Leaf("leaf-$index-$it", index + it / 10.0) })
        }


    //-----------------------------------------------------------------------------------------------------------------
    private fun unionOf(variant: DataContract): DataContract =
        DataContract(DataType.Union(listOf(
            DataVariant(VariantId("native"), variant.structural),
            DataVariant(VariantId("text"), DataType.Scalar(ScalarKind.Text)))))


    private fun <T> inParallel(count: Int, action: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(count) { runnable ->
            Thread(runnable, "native-value-gate").also { it.isDaemon = true }
        }
        try {
            val futures = List(count) { pool.submit<T> { action() } }
            return futures.map { it.get(gateSeconds * 2, TimeUnit.SECONDS) }
        }
        finally {
            pool.shutdownNow()
        }
    }


    private companion object {
        const val racers = 2
        const val gateSeconds = 20L
        const val stressSeconds = 120L
        const val threads = 8
        const val rounds = 100
        const val itemCount = 48
        const val rotationStep = 7
    }
}
