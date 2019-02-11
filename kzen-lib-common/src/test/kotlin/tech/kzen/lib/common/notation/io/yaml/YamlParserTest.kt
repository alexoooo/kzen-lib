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
        val notation = parseGraph("")

        assertTrue(notation.coalesce.values.isEmpty())
    }


    @Test
    fun parseSimpleYaml() {
        val notation = parseGraph("""
Foo:
  bar: "baz"
""")

        assertEquals("baz", notation.getString(location("Foo"), attribute("bar")))
    }


    @Test
    fun parseComplexYaml() {
        val notation = parseGraph("""
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
        val notation = parseGraph("""
"Foo bar":
  bar: "baz"
""")

        assertEquals("baz", notation.getString(location("Foo bar"), attribute("bar")))
    }


    @Test
    fun parseEscapeInDoubleQuote() {
        val notation = parseGraph("""
Foo:
  bar: "baz\""
""")

        assertEquals("baz\"", notation.getString(location("Foo"), attribute("bar")))
    }


//    @Test
//    fun parseComplicatedYaml() {
//        val notation = parseGraph("""
//"__ANON__20190131T143114_226Z":
//  is: "action/action-browser.yaml#/OpenBrowser"
//""")
//
//        assertEquals("action/action-browser.yaml#/OpenBrowser",
//                notation.getString(location("__ANON__20190131T143114_226Z"), attribute("is")))
//    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun deparseSimpleAddition() {
        val initial = ""

        val expected = "Foo:\n  bar: baz"

        assertEquals(expected, deparse(initial, expected))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseBundle(doc: String): BundleNotation {
        return yamlParser.parseBundle(IoUtils.utf8Encode(doc))
    }


    private fun parseGraph(doc: String): GraphNotation {
        val bundleNotation = parseBundle(doc)
        return GraphNotation(BundleTree(mapOf(
                mainPath to bundleNotation)))
    }


    private fun deparse(initial: String, expected: String): String {
        return IoUtils.utf8Decode(yamlParser.deparseBundle(
                yamlParser.parseBundle(IoUtils.utf8Encode(expected)),
                IoUtils.utf8Encode(initial)))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributePath {
        return AttributePath.parse(attribute)
    }
}
