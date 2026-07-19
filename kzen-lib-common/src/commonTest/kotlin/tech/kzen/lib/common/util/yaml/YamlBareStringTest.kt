package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals


class YamlBareStringTest {
    //-----------------------------------------------------------------------------------------------------------------
    // Built via char codes so the source stays plain ASCII (no literal backslash / form-feed).
    private val bs = 92.toChar()   // backslash
    private val ff = 12.toChar()   // form feed (\f)
    private val dq = 34.toChar()   // double quote


    private fun parseValue(document: String): String {
        val node = YamlParser.parse(document) as YamlMap
        return (node.values.values.single() as YamlString).value
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun windowsPathBare() {
        assertEquals("C:${bs}~${bs}foo", parseValue("test: C:${bs}~${bs}foo"))
    }


    @Test
    fun urlBare() {
        assertEquals("https://kzen.tech", parseValue("url: https://kzen.tech"))
    }


    @Test
    fun expressionBare() {
        assertEquals("a + b * 2", parseValue("expr: a + b * 2"))
    }


    @Test
    fun moduloExpressionBare() {
        assertEquals("number % 3 == 0", parseValue("code: number % 3 == 0"))
    }


    @Test
    fun trailingCommentStrippedFromBareValue() {
        assertEquals("foo bar", parseValue("key: foo bar # trailing comment"))
    }


    @Test
    fun hashWithoutLeadingSpaceIsLiteral() {
        assertEquals("a#b", parseValue("key: a#b"))
    }


    @Test
    fun colonWithoutSpaceIsScalarNotEntry() {
        val node = YamlParser.parse("key:value")
        assertEquals(YamlString("key:value"), node)
    }


    @Test
    fun nestedColonInInlineValueIsScalar() {
        // `key: a: b` must not nest — the inline value is the literal scalar "a: b".
        assertEquals("a: b", parseValue("key: a: b"))
    }


    @Test
    fun listItemWithBackslashIsScalar() {
        val node = YamlParser.parse("""
- C:${bs}foo
- C:${bs}bar
""") as YamlList
        assertEquals("C:${bs}foo", (node.values[0] as YamlString).value)
        assertEquals("C:${bs}bar", (node.values[1] as YamlString).value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleQuoteDoublingInValue() {
        assertEquals("it's", parseValue("key: 'it''s'"))
    }


    @Test
    fun singleQuoteDoublingInKey() {
        val node = YamlParser.parse("'a''b': x") as YamlMap
        assertEquals("x", node.values["a'b"]?.toObject())
    }


    @Test
    fun legacySingleQuoteBackslashQuote() {
        assertEquals("a'b", parseValue("key: 'a${bs}'b'"))
    }


    @Test
    fun legacySingleQuoteBackslashBackslash() {
        assertEquals("a${bs}b", parseValue("key: 'a${bs}${bs}b'"))
    }


    @Test
    fun formFeedUnescapeInDoubleQuote() {
        assertEquals("a${ff}b", parseValue("key: ${dq}a${bs}fb${dq}"))
    }


    @Test
    fun formFeedUnescapeInSingleQuote() {
        assertEquals("a${ff}b", parseValue("key: 'a${bs}fb'"))
    }
}
