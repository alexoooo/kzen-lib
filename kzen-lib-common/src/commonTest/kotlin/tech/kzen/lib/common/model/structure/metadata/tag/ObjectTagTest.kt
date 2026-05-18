package tech.kzen.lib.common.model.structure.metadata.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class ObjectTagTest {
    @Test
    fun parseRoundTrip() {
        val tag = ObjectTag.parse("logic")
        assertEquals(ObjectTag("logic"), tag)
        assertEquals("logic", tag.asString())
        assertEquals("logic", tag.toString())
    }


    @Test
    fun acceptsHyphenAndUnderscore() {
        assertTrue(ObjectTag.matches("a"))
        assertTrue(ObjectTag.matches("a-b"))
        assertTrue(ObjectTag.matches("a_b"))
        assertTrue(ObjectTag.matches("alpha-Numeric_42"))
    }


    @Test
    fun rejectsInvalidValues() {
        assertFalse(ObjectTag.matches(""))
        assertFalse(ObjectTag.matches(" "))
        assertFalse(ObjectTag.matches("with space"))
        assertFalse(ObjectTag.matches("1numericStart"))
        assertFalse(ObjectTag.matches("-leadingDash"))

        assertFails { ObjectTag("") }
        assertFails { ObjectTag("with space") }
        assertFails { ObjectTag("1numericStart") }
    }


    @Test
    fun digestStability() {
        val a = ObjectTag("logic")
        val b = ObjectTag("logic")
        val c = ObjectTag("hidden")

        assertEquals(a.digest(), b.digest())
        assertNotEquals(a.digest(), c.digest())
    }
}
