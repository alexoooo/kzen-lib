package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals


class YamlBlockScalarTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val nl = "" + 10.toChar()          // newline
    private val cr = 13.toChar()
    private val lf = 10.toChar()
    private val bs = 92.toChar()               // backslash
    private val dq = 34.toChar()               // double quote

    private fun lines(vararg parts: String): String =
        parts.joinToString(nl)

    private fun parseValue(document: String): String {
        val node = YamlParser.parse(document) as YamlMap
        return (node.values.values.single() as YamlString).value
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun stripBlockScalar() {
        assertEquals(lines("line1", "line2"), parseValue("""
key: |-
  line1
  line2
"""))
    }


    @Test
    fun clipBlockScalarKeepsOneTrailingNewline() {
        assertEquals(lines("line1", "line2") + nl, parseValue("""
key: |
  line1
  line2
"""))
    }


    @Test
    fun blankInteriorLineIsSignificant() {
        assertEquals(lines("line1", "", "line2"), parseValue("""
key: |-
  line1

  line2
"""))
    }


    @Test
    fun hashLineInBodyIsLiteral() {
        assertEquals(lines("# not a comment", "real"), parseValue("""
key: |-
  # not a comment
  real
"""))
    }


    @Test
    fun deeperIndentPreservedRelativeToBlockIndent() {
        assertEquals(lines("a", "  b"), parseValue("""
key: |-
  a
    b
"""))
    }


    @Test
    fun listItemBlockScalar() {
        val node = YamlParser.parse("""
- |-
  a
  b
""") as YamlList
        assertEquals(lines("a", "b"), (node.values.single() as YamlString).value)
    }


    @Test
    fun emptyBodyBlockScalar() {
        val node = YamlParser.parse("""
key: |-
next: x
""") as YamlMap
        assertEquals("", (node.values["key"] as YamlString).value)
        assertEquals("x", node.values["next"]?.toObject())
    }


    @Test
    fun crlfBlockScalar() {
        val doc = "key: |-" + cr + lf + "  a" + cr + lf + "  b" + cr + lf + "next: x"
        val node = YamlParser.parse(doc) as YamlMap
        assertEquals(lines("a", "b"), (node.values["key"] as YamlString).value)
        assertEquals("x", node.values["next"]?.toObject())
    }


    @Test
    fun eofWithoutTrailingNewline() {
        val doc = "key: |-" + nl + "  a"
        assertEquals("a", parseValue(doc))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unparseMultiLineStringAsBlock() {
        val node = YamlString(lines("line1", "line2"))
        assertEquals(lines("|-", "  line1", "  line2"), YamlParser.unparse(node))
    }


    @Test
    fun unparseQuotedKotlinLiteralAsBlock() {
        // Starts with `"` and contains backslashes → not bare / single-quotable → emits |-.
        val value = dq + "C:" + bs + "~" + bs + "data" + dq
        assertEquals(lines("|-", "  $value"), YamlParser.unparse(YamlString(value)))
    }
}
