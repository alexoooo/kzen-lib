package tech.kzen.lib.common.exec.data.value

import com.sun.management.ThreadMXBean
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DefaultNativeTypeResolver
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import tech.kzen.lib.common.exec.data.type.FieldId
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.reflect.typeOf


class DataValueJvmTest {
    @Test
    fun repeatedPrimitiveFieldReadsAllocateNothingAfterSetup() {
        val value = LiteralDataValues.lift(recordOf("count" to 42))
        val node = value.access.field(value.root, FieldId("count"))
        repeat(20_000) { value.access.readLong(node) }

        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        var sum = 0L
        repeat(100_000) { sum += value.access.readLong(node) }
        val allocated = bean.getThreadAllocatedBytes(thread) - before

        assertEquals(4_200_000L, sum)
        assertTrue(allocated <= 4_096, "primitive-read loop allocated $allocated bytes")
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun nativeResolverAcceptsExactCanonicalProjectionAndRejectsOverflow() {
        DefaultNativeTypeResolver().use { resolver ->
            val expected = resolver.describe(typeOf<Int>())
            val exact = LiteralDataValues.lift(
                13L,
                DataContract(DataType.Scalar(ScalarKind.Integer(32))))
            val overflow = LiteralDataValues.lift(
                Int.MAX_VALUE.toLong() + 1,
                DataContract(DataType.Scalar(ScalarKind.Integer(32))))

            assertEquals(TypeAcceptance.Accepted, resolver.isAssignable(expected, exact))
            assertIs<TypeAcceptance.Rejected>(resolver.isAssignable(expected, overflow))
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun decimalMaterializationAndNativeProjectionRemainExact() {
        val text = "12345678901234567890.1234567890123456789"
        val decimal = LiteralDataValues.lift(
            text,
            DataContract(DataType.Scalar(ScalarKind.Decimal)))

        assertEquals(BigDecimal(text), decimal.materializeJvm())
        DefaultNativeTypeResolver().use { resolver ->
            assertEquals(
                TypeAcceptance.Accepted,
                resolver.isAssignable(resolver.describe(typeOf<BigDecimal>()), decimal))
        }
    }
}
