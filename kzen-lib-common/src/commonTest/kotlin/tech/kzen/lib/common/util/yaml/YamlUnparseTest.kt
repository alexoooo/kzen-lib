package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals


class YamlUnparseTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun bareString() {
        val node = YamlString("foo")
        assertEquals("foo", YamlParser.unparse(node))
    }


    @Test
    fun stringWithSpace() {
        val node = YamlString("foo bar")
        assertEquals("foo bar", YamlParser.unparse(node))
    }


    @Test
    fun stringWithDoubleQuote() {
        val node = YamlString("foo\"bar\"")
        assertEquals("'foo\"bar\"'", YamlParser.unparse(node))
    }


    @Test
    fun stringWithSingleQuote() {
        val node = YamlString("foo'bar'")
        assertEquals("\"foo'bar'\"", YamlParser.unparse(node))
    }


    @Test
    fun stringWithSingleAndDouble() {
        val node = YamlString("foo'bar\"")
        assertEquals("\"foo'bar\\\"\"", YamlParser.unparse(node))
    }


    @Test
    fun mapOfString() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlString("bar"),
                        "baz" to YamlString("buh")
                ))

        assertEquals("foo: bar\nbaz: buh", YamlParser.unparse(node))
    }


    @Test
    fun mapOfMap() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlMap(mapOf(
                                "bar" to YamlString("baz")
                        ))
                ))

        assertEquals("foo:\n  bar: baz", YamlParser.unparse(node))
    }


    @Test
    fun mapOfList() {
        val node =
                YamlMap(mapOf(
                        "foo" to YamlList(listOf(
                                YamlString("bar")
                        ))
                ))

        assertEquals("foo:\n  - bar", YamlParser.unparse(node))
    }


    @Test
    fun mapOfListWithSpecialCharacters() {
        val node =
                YamlMap(mapOf(
                        "Foo/bar baz" to YamlList(listOf(
                                YamlString("foo/bar"),
                                YamlString("hello world")
                        ))
                ))

        assertEquals(
            "Foo/bar baz:\n" +
                "  - foo/bar\n" +
                "  - hello world",
            YamlParser.unparse(node))
    }
}
