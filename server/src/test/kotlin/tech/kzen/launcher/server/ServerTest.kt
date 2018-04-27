package tech.kzen.launcher.server

import tech.kzen.launcher.common.getAnswer
import org.junit.Assert.assertEquals
import kotlin.test.Test


class ServerTest {
    @Test
    fun `simple test`() {
        assertEquals(42, getAnswer())
    }
}
