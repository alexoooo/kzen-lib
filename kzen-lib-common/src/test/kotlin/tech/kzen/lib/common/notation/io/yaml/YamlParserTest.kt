package tech.kzen.lib.common.notation.io.yaml

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = BundlePath.parse("main.yaml")
    private val yamlParser = YamlNotationParser()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseQuotedStringAttribute() {
        val notation = yamlParser.parseAttribute("\"foo\"")
        assertEquals("foo", (notation as ScalarAttributeNotation).value)
    }


    @Test
    fun parseBareStringAttribute() {
        val notation = yamlParser.parseAttribute("bar")
        assertEquals("bar", (notation as ScalarAttributeNotation).value)
    }



    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseEmptyYaml() {
        val notation = parseProject("")

        assertTrue(notation.coalesce.values.isEmpty())
    }


    @Test
    fun parseSimpleYaml() {
        val notation = parseProject("""
Foo:
  bar: "baz"
""")

        assertEquals("baz", notation.getString(location("Foo"), attribute("bar")))
    }


    @Test
    fun parseComplexYaml() {
        val notation = parseProject("""
# Hello
Foo:
  bar:
  - hello
  - world
  baz:
    hello: 'world'
""")

        assertEquals("hello", notation.getString(location("Foo"), attribute("bar.0")))
        assertEquals("world", notation.getString(location("Foo"), attribute("bar.1")))
        assertEquals("world", notation.getString(location("Foo"), attribute("baz.hello")))
    }


    @Test
    fun parseSpaceInKey() {
        val notation = parseProject("""
"Foo bar":
  bar: "baz"
""")

        assertEquals("baz", notation.getString(location("Foo bar"), attribute("bar")))
    }


    @Test
    fun parseEscapeInDoubleQuote() {
        val notation = parseProject("""
Foo:
  bar: "baz\""
""")

        assertEquals("baz\"", notation.getString(location("Foo"), attribute("bar")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun deparseSimpleAddition() {
        val initial = ""

        val expected = "Foo:\n  bar: baz"

        assertEquals(expected, deparse(initial, expected))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseBundle(doc: String): BundleNotation {
        return yamlParser.parseBundle(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): GraphNotation {
        val bundleNotation = parseBundle(doc)
        return GraphNotation(BundleTree(mapOf(
                mainPath to bundleNotation)))
    }


    private fun deparse(initial: String, expected: String): String {
        return IoUtils.utf8ToString(yamlParser.deparseBundle(
                yamlParser.parseBundle(IoUtils.stringToUtf8(expected)),
                IoUtils.stringToUtf8(initial)))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributePath {
        return AttributePath.parse(attribute)
    }
}
