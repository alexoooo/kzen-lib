package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals


class YamlRoundTripTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val nl = "" + 10.toChar()      // newline
    private val bs = 92.toChar()           // backslash
    private val ff = 12.toChar()           // form feed
    private val tab = 9.toChar()
    private val dq = 34.toChar()           // double quote


    private fun corpus(): List<String> = listOf(
        "foo",
        "foo bar",
        "42",
        "true",
        "null",
        "-5",
        "a + b * 2",
        "number % 3 == 0",
        "https://kzen.tech",
        "C:" + bs + "~" + bs + "foo",                                  // Windows path
        "foo" + dq + "bar" + dq,                                       // embedded double quotes
        "it's",                                                        // single quote
        "café",                                                        // non-ASCII
        "a" + ff + "b",                                                // form feed
        "a" + tab + "b",                                               // tab
        "line1" + nl + "line2",                                        // multi-line
        "fun f() {" + nl + "    return " + dq + "x" + dq + nl + "}",   // multi-line Kotlin w/ quotes
        dq + "C:" + bs + "~" + bs + "data" + dq,                       // quoted literal w/ backslash
        "trailing" + nl,                                               // trailing newline
        "",                                                            // empty
        "  leading spaces",                                            // leading whitespace
        "trailing spaces  ")                                           // trailing whitespace


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun standaloneStringsRoundTrip() {
        for (value in corpus()) {
            val node = YamlString(value)
            val roundTrip = YamlParser.parse(YamlParser.unparse(node))
            assertEquals(node, roundTrip, "standalone: [$value]")
        }
    }


    @Test
    fun mapValuesRoundTrip() {
        for (value in corpus()) {
            val node = YamlMap(mapOf("key" to YamlString(value)))
            val roundTrip = YamlParser.parse(YamlParser.unparse(node))
            assertEquals(node, roundTrip, "map value: [$value]")
        }
    }


    @Test
    fun listItemsRoundTrip() {
        val node = YamlList(corpus().map { YamlString(it) })
        val roundTrip = YamlParser.parse(YamlParser.unparse(node))
        assertEquals(node, roundTrip)
    }


    @Test
    fun nestedStructureRoundTrips() {
        val node = YamlMap(mapOf(
            "outer" to YamlMap(mapOf(
                "path" to YamlString("C:" + bs + "temp" + bs + "x"),
                "list" to YamlList(listOf(
                    YamlString("a"),
                    YamlString("b" + nl + "c"))))),
            "flag" to YamlString("true")))
        val roundTrip = YamlParser.parse(YamlParser.unparse(node))
        assertEquals(node, roundTrip)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Legacy on-disk forms the OLD emitter produced must still parse to identical values.
    @Test
    fun legacySingleQuotedDoubledBackslash() {
        // Old form: path: '"C:\\~\\data\\measurements-100000.txt"'
        val inner = dq + "C:" + bs + bs + "~" + bs + bs + "data" + bs + bs + "measurements-100000.txt" + dq
        val document = "path: '" + inner + "'"

        val expected = dq + "C:" + bs + "~" + bs + "data" + bs + "measurements-100000.txt" + dq
        val node = YamlParser.parse(document) as YamlMap
        assertEquals(expected, node.values["path"]?.toObject())
    }


    @Test
    fun legacyDoubleQuotedBackslash() {
        val document = "x: " + dq + "a" + bs + bs + "b" + dq   // x: "a\\b"
        val node = YamlParser.parse(document) as YamlMap
        assertEquals("a" + bs + "b", node.values["x"]?.toObject())
    }


    @Test
    fun legacyBareStringUnchanged() {
        // The old regex-bare charset ([0-9a-zA-Z_-/.] + interior space) is a no-op under unescape,
        // so those values are byte-identical.
        val document = "Foo/bar baz: hello world"
        val node = YamlParser.parse(document) as YamlMap
        assertEquals("hello world", node.values["Foo/bar baz"]?.toObject())
    }
}
