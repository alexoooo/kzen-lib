package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


// Runs on BOTH jvm and js, which is the point of it. YamlNode.ofObject dispatches on the runtime type of an
// `Any?`, and Kotlin/JS collapses every number but Long into a JS `number` — so `is Int` there also matches a
// Float/Double, and a per-type branch list silently means something different on each platform. ofObject's
// numeric branches are unreachable from kzen's own callers today (both pass String / Map<String, String>), so
// nothing else would notice if they diverged; this pins them.
class YamlNodeTest {
    private class Unsupported


    private fun assertRenders(value: Any?, expected: String) {
        assertEquals(YamlString(expected), YamlNode.ofObject(value), "ofObject($value)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun numbersRenderAsStrings() {
        // NB: deliberately no whole-valued Double/Float fixture — 0.0.toString() is "0.0" on jvm but "0" on js.
        // That divergence predates the single `is Number` branch and is inert given the callers above; it is not
        // something this test should pretend agrees.
        assertRenders(42, "42")
        assertRenders(5L, "5")
        assertRenders(3.14, "3.14")
        assertRenders(1.5f, "1.5")

        // Byte/Short reach the same branch as the rest. Before the `is Number` collapse these threw on jvm while
        // already rendering on js, since `is Int` matches them there.
        assertRenders(7.toByte(), "7")
        assertRenders(9.toShort(), "9")
    }


    @Test
    fun stringsBooleansAndNullRenderAsStrings() {
        assertRenders("text", "text")
        assertRenders(true, "true")
        assertRenders(false, "false")
        assertRenders(null, "null")
    }


    @Test
    fun structuresRecurse() {
        assertEquals(
            YamlList(listOf(YamlString("1"), YamlString("a"))),
            YamlNode.ofObject(listOf(1, "a")))

        assertEquals(
            YamlMap(mapOf("k" to YamlString("2"))),
            YamlNode.ofObject(mapOf("k" to 2)))
    }


    @Test
    fun unsupportedValueThrows() {
        assertFailsWith<UnsupportedOperationException> {
            YamlNode.ofObject(Unsupported())
        }
    }
}
