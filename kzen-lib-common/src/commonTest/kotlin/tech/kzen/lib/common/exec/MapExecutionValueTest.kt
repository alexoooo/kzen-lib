package tech.kzen.lib.common.exec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class MapExecutionValueTest {
    @Test
    fun digestIgnoresEntryOrder() {
        val forward = MapExecutionValue(linkedMapOf(
            "first" to TextExecutionValue("one"),
            "second" to TextExecutionValue("two")))
        val reverse = MapExecutionValue(linkedMapOf(
            "second" to TextExecutionValue("two"),
            "first" to TextExecutionValue("one")))

        assertEquals(forward, reverse)
        assertEquals(forward.digest(), reverse.digest())
    }


    @Test
    fun digestIncludesKeysAndValues() {
        val original = MapExecutionValue(mapOf("key" to TextExecutionValue("value")))

        assertNotEquals(
            original.digest(),
            MapExecutionValue(mapOf("other" to TextExecutionValue("value"))).digest())
        assertNotEquals(
            original.digest(),
            MapExecutionValue(mapOf("key" to TextExecutionValue("other"))).digest())
    }


    @Test
    fun listDigestRemainsOrderSensitive() {
        val forward = ListExecutionValue(listOf(TextExecutionValue("one"), TextExecutionValue("two")))
        val reverse = ListExecutionValue(listOf(TextExecutionValue("two"), TextExecutionValue("one")))

        assertNotEquals(forward.digest(), reverse.digest())
    }
}
