package tech.kzen.lib.common.exec.logic.trace.model

import tech.kzen.lib.common.exec.ExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals


class LogicTraceRangeTest {
    @Test
    fun aRangeSurvivesTheWire() {
        val range = LogicTraceRange(
            size = 5_000_000,
            offset = 4_999_998,
            entries = listOf(ExecutionValue.of("a"), ExecutionValue.of(mapOf("item" to "b", "value" to "c"))))

        val wire = range.asExecutionValue().toJsonCollection()
        assertEquals(range, LogicTraceRange.ofExecutionValue(ExecutionValue.fromJsonCollection(wire)))
    }
}
