package tech.kzen.lib.common.exec.data.value.overlay

import com.sun.management.ThreadMXBean
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.recordOf
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue


/**
 * A metadata record made by a shape costs its composition, not a contract: measured as this thread's allocated
 * bytes per record over a fresh value per record, the record's value read and its contract combined with a payload.
 * The same record composed by `ValueMetadata.of(DataOverlay.record(...))` is measured beside it for the record.
 */
class MetadataShapeAllocationTest {
    private val warmupRecords = 20_000
    private val measuredRecords = 50_000
    private val distinctValues = 1024

    // A record is a few small objects (its access, metadata, value views and the list of its values)
    private val shapedBudgetBytes = 400L

    private val text = DataContract(DataType.Scalar(ScalarKind.Text))
    private val total = FieldId("total")

    private val file = ValueMetadata.of(DataOverlay.record(null, listOf(
        FieldId("name") to LiteralDataValues.lift("input.csv", text),
        FieldId("path") to LiteralDataValues.lift("/data/input.csv", text))))
    private val parsed = ValueMetadata.of(DataOverlay.record(null, listOf(FieldId("parent") to file.value)))

    private val payload = LiteralDataValues.lift(recordOf("x" to "1"), DataContract(DataType.Record(listOf(
        DataField(FieldId("x"), text.structural)))))

    private val values = List(distinctValues) { LiteralDataValues.lift(it.toString(), text) }

    // Keeps the reads observable
    private var sink = 0


    @Test
    fun aShapedRecordCostsItsCompositionNotAContract() {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true

        val shape = MetadataShape(parsed.contract, listOf(total to text))
        val shaped = perRecord(bean) { index ->
            shape.metadata(parsed, listOf(values[index % distinctValues]))
        }
        val composed = perRecord(bean) { index ->
            ValueMetadata.of(DataOverlay.record(parsed.value, listOf(total to values[index % distinctValues])))
        }

        println("WR11 metadata record: shaped $shaped, composed $composed bytes per record")
        assertTrue(shaped < shapedBudgetBytes, "$shaped bytes per shaped record")
        assertTrue(composed > shapedBudgetBytes, "$composed bytes per composed record")
    }


    private fun perRecord(bean: ThreadMXBean, make: (Int) -> ValueMetadata): Long {
        var checksum = 0
        repeat(warmupRecords) { checksum += read(make(it)) }

        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(measuredRecords) { checksum += read(make(it)) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before

        sink = checksum
        return allocated / measuredRecords
    }


    private fun read(metadata: ValueMetadata): Int {
        val value: DataValue = payload.withMetadata(metadata)
        val node = metadata.access.field(metadata.root, total)
        return metadata.access.readText(node).length + System.identityHashCode(value.contract)
    }
}
