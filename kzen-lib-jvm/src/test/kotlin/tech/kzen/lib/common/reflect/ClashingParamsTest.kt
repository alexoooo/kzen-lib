package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.objects.clash.ClashingParamsHolder
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tech.kzen.lib.server.objects.clash.alpha.Payload as AlphaPayload
import tech.kzen.lib.server.objects.clash.omega.Payload as OmegaPayload


/**
 * The load-bearing assertion is that the generated module compiles at all — same-simple-name
 * parameter types from different packages have no import spelling that works. The registry is
 * queried directly (not through [tech.kzen.lib.common.reflect.GlobalMirror]) so the JVM reflective
 * fallback can't stand in for the generated registration.
 */
class ClashingParamsTest {
    private val registry = JvmGraphTestUtils.reflectionRegistry

    private val className = ClassName(
        "tech.kzen.lib.server.objects.clash.ClashingParamsHolder")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `same simple name parameter types are registered`() {
        assertTrue(registry.contains(className))
        assertEquals(listOf("first", "second"), registry.constructorArgumentNames(className))
    }


    @Test
    fun `same simple name parameter types are constructed in declaration order`() {
        val instance = registry.create(
            className, listOf(AlphaPayload("a"), OmegaPayload("b"))
        ) as ClashingParamsHolder

        assertEquals("a", instance.first.value)
        assertEquals("b", instance.second.value)
    }
}
