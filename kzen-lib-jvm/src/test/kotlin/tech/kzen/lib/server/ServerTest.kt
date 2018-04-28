package tech.kzen.lib.server

import tech.kzen.lib.common.getAnswer
import org.junit.Assert.assertEquals
import kotlin.test.Test


class ServerTest {
    @Test
    fun `simple test`() {
        assertEquals(42, getAnswer())
    }
}
