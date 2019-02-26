package tech.kzen.lib.common.structure.notation.io.yaml

import tech.kzen.lib.common.structure.notation.format.YamlList
import tech.kzen.lib.common.structure.notation.format.YamlMap
import tech.kzen.lib.common.structure.notation.format.YamlString
import kotlin.test.Test
import kotlin.test.assertEquals


class YamlNodeAsStringTest {
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
    fun mapOfString() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlString("bar"),
                        "baz" to YamlString("buh")
                ))

        assertEquals("foo: bar\nbaz: buh", node.asString())
    }


    @Test
    fun mapOfMap() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlMap(mapOf(
                                "bar" to YamlString("baz")
                        ))
                ))

        assertEquals("foo:\n  bar: baz", node.asString())
    }


    @Test
    fun mapOfList() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlList(listOf(
                                YamlString("bar")
                        ))
                ))

        assertEquals("foo:\n  - bar", node.asString())
    }
}
