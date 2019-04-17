package tech.kzen.lib.common.structure.notation.io.yaml

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlNotationParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = DocumentPath.parse("main.yaml")
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


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun deparseSimpleAddition() {
        val initial = ""

        val expected = "Foo:\n  bar: baz"

        assertEquals(expected, deparse(initial, expected))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseDocument(doc: String): DocumentNotation {
        return yamlParser.parseDocument(IoUtils.utf8Encode(doc))
    }


    private fun parseGraph(doc: String): GraphNotation {
        val documentNotation = parseDocument(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
                mainPath to documentNotation)))
    }


    private fun deparse(initial: String, expected: String): String {
        return IoUtils.utf8Decode(yamlParser.deparseDocument(
                yamlParser.parseDocument(IoUtils.utf8Encode(expected)),
                IoUtils.utf8Encode(initial)))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributePath {
        return AttributePath.parse(attribute)
    }
}
