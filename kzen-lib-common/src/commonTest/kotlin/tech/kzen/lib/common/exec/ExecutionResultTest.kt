package tech.kzen.lib.common.exec

import kotlin.test.Test
import kotlin.test.assertEquals


class ExecutionResultTest {
    @Test
    fun nullPointerException() {
        val exception = NullPointerException("foo")
        val executionFailure = ExecutionFailure.ofException(exception)
        assertEquals("Null Pointer: foo", executionFailure.errorMessage)
    }
}