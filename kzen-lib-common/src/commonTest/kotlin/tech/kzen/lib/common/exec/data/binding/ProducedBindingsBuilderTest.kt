package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.TextExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame


class ProducedBindingsBuilderTest {
    private val first = BindingName("first")
    private val second = BindingName("second")
    private val firstValue = LiteralDataValues.lift("one")
    private val schema = BindingSchema.of(
        BindingDefinition(first, firstValue.contract),
        BindingDefinition(second, firstValue.contract, DataPresence.Optional))

    @Test
    fun repeatedWritesAreLastWinsWhileEnumerationStaysInSchemaOrder() {
        val builder = ProducedBindingsBuilder(schema)
        val old = LiteralDataValues.lift("old")
        val latest = LiteralDataValues.lift("latest")
        builder.set(second, LiteralDataValues.lift("two"))
        builder.set(first, old)
        builder.set(first, latest)

        val settled = builder.settle()
        assertEquals(listOf(first, second), settled.entries().map { it.first.name })
        assertSame(latest, settled.requireValue(first))
        assertEquals(listOf(second, first, first), builder.yieldChronology())
        assertEquals(BindingOrigin.Produced, (settled[first] as BindingState.Bound).origin)
    }

    @Test
    fun settleRejectsMissingRequiredAndSetRejectsUnknownOrWrongType() {
        val missing = assertFailsWith<DataException> { ProducedBindingsBuilder(schema).settle() }
        assertEquals(DataProblem.missingValue, missing.problem.code)

        val builder = ProducedBindingsBuilder(schema)
        assertEquals(
            DataProblem.invalidIdentifier,
            assertFailsWith<DataException> {
                builder.set(BindingName("unknown"), firstValue)
            }.problem.code)
        assertEquals(
            DataProblem.incompatibleType,
            assertFailsWith<DataException> {
                builder.set(first, LiteralDataValues.lift(1))
            }.problem.code)
    }

    @Test
    fun settleAppliesAnOutputDefaultOnceWithDefaultedOrigin() {
        val defaulted = BindingName("defaulted")
        val default = DataDefault(DataSnapshot.of(firstValue.type, TextExecutionValue("fallback")))
        val builder = ProducedBindingsBuilder(BindingSchema.of(BindingDefinition(
            defaulted,
            firstValue.contract,
            DataPresence.Defaulted(default))))

        val state = builder.settle()[defaulted] as BindingState.Bound
        assertEquals(BindingOrigin.Defaulted, state.origin)
        assertEquals("fallback", state.value.access.readText(state.value.root))
    }
}
