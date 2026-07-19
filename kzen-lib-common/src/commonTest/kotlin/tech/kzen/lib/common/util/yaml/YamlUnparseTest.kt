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
        // Both quote kinds are valid inside a plain scalar, so these now emit bare.
        val node = YamlString("foo\"bar\"")
        assertEquals("foo\"bar\"", YamlParser.unparse(node))
    }


    @Test
    fun stringWithSingleQuote() {
        val node = YamlString("foo'bar'")
        assertEquals("foo'bar'", YamlParser.unparse(node))
    }


    @Test
    fun stringWithSingleAndDouble() {
        val node = YamlString("foo'bar\"")
        assertEquals("foo'bar\"", YamlParser.unparse(node))
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


    //-----------------------------------------------------------------------------------------------------------------
    private val bs = 92.toChar()
    private val dq = 34.toChar()


    @Test
    fun dashNumberStaysBare() {
        assertEquals("-5", YamlParser.unparse(YamlString("-5")))
    }


    @Test
    fun windowsPathValueStaysBare() {
        assertEquals("C:${bs}foo", YamlParser.unparse(YamlString("C:${bs}foo")))
    }


    @Test
    fun colonSpaceForcesSingleQuote() {
        assertEquals("'a: b'", YamlParser.unparse(YamlString("a: b")))
    }


    @Test
    fun trailingColonForcesSingleQuote() {
        assertEquals("'foo:'", YamlParser.unparse(YamlString("foo:")))
    }


    @Test
    fun dashSpaceForcesSingleQuote() {
        assertEquals("'- item'", YamlParser.unparse(YamlString("- item")))
    }


    @Test
    fun spaceHashForcesSingleQuote() {
        assertEquals("'a #b'", YamlParser.unparse(YamlString("a #b")))
    }


    @Test
    fun keyWithBackslashIsQuoted() {
        // Keys keep the restricted bare charset, so a backslash key must be double-quoted.
        assertEquals("${dq}C:${bs}${bs}foo${dq}", YamlParser.unparseKey("C:${bs}foo"))
    }


    @Test
    fun keyWithColonIsQuoted() {
        assertEquals("'a: b'", YamlParser.unparseKey("a: b"))
    }
}
