package tech.kzen.lib.common.notation.io.yaml

import tech.kzen.lib.common.notation.format.YamlMap
import tech.kzen.lib.common.notation.format.YamlNodeParser
import tech.kzen.lib.common.notation.format.YamlString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlNodeTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun bareString() {
        val node = YamlString("foo")
        assertEquals("foo", node.asString())
    }


    @Test
    fun stringWithSpace() {
        val node = YamlString("foo bar")
        assertEquals("\"foo bar\"", node.asString())
    }


    @Test
    fun stringWithDoubleQuote() {
        val node = YamlString("foo\"bar\"")
        assertEquals("'foo\"bar\"'", node.asString())
    }


    @Test
    fun stringWithSingleQuote() {
        val node = YamlString("foo'bar'")
        assertEquals("\"foo'bar'\"", node.asString())
    }


    @Test
    fun stringWithSingleAndDouble() {
        val node = YamlString("foo'bar\"")
        assertEquals("\"foo'bar\\\"\"", node.asString())
    }


    @Test
    fun parseNestedMapWithPound() {
        val node = YamlNodeParser.parse("""
Foo:
  is: "#"
""") as YamlMap

        assertTrue(node.values["Foo"] is YamlMap)
    }

}
