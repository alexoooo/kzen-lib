package tech.kzen.lib.common.service.parse

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
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

        assertTrue(notation.coalesce.map.isEmpty())
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
    fun unparseSimpleAddition() {
        val initial = ""

        val expected = "Foo:\n  bar: baz"

        assertEquals(expected, unparse(initial, expected))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unparsePreservesUnchangedObjectComment() {
        // Edit object A; B is unchanged, so its own-line comment survives byte-identical.
        val previous = "A:\n  x: 1\n\n# comment on B\nB:\n  y: 2"
        val newDocument = "A:\n  x: 9\n\nB:\n  y: 2"
        val expected = "A:\n  x: 9\n\n# comment on B\nB:\n  y: 2"

        assertEquals(expected, unparse(previous, newDocument))
    }


    @Test
    fun unparsePreservesLeadingDocumentComment() {
        // A leading document comment (before the first object) is preserved even when that object changes.
        val previous = "# header comment\n\nA:\n  x: 1"
        val newDocument = "A:\n  x: 9"
        val expected = "# header comment\n\nA:\n  x: 9"

        assertEquals(expected, unparse(previous, newDocument))
    }


    @Test
    fun unparseNormalizesInterObjectBlankLines() {
        // Inter-object blank-line count normalizes to a single blank line (accepted first-cut behaviour).
        val previous = "A:\n  x: 1\n\n\n\nB:\n  y: 2"
        val newDocument = "A:\n  x: 9\n\nB:\n  y: 2"
        val expected = "A:\n  x: 9\n\nB:\n  y: 2"

        assertEquals(expected, unparse(previous, newDocument))
    }


    @Test
    fun unparseDropsCommentInsideChangedObject() {
        // A comment INSIDE a changed object is lost when it is re-serialized (accepted first-cut behaviour).
        val previous = "A:\n  # inner comment\n  x: 1\n\nB:\n  y: 2"
        val newDocument = "A:\n  x: 9\n\nB:\n  y: 2"
        val expected = "A:\n  x: 9\n\nB:\n  y: 2"

        assertEquals(expected, unparse(previous, newDocument))
    }


    @Test
    fun unparseFallsBackWhenTemplateUnmatched() {
        // A template whose segments don't parse degrades gracefully to the plain house serialization.
        val garbageTemplate = "%%% not valid notation %%%"
        val newDocument = "A:\n  x: 1"

        assertEquals(unparse("", newDocument), unparse(garbageTemplate, newDocument))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseDocumentObjects(doc: String): DocumentObjectNotation {
        return yamlParser.parseDocumentObjects(doc)
    }


    private fun parseGraph(doc: String): GraphNotation {
        val documentNotation = parseDocumentObjects(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
                mainPath to DocumentNotation(
                        documentNotation,
                        null))))
    }


    private fun unparse(initial: String, expected: String): String {
        return yamlParser.unparseDocument(
                yamlParser.parseDocumentObjects(expected),
                initial)
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributePath {
        return AttributePath.parse(attribute)
    }
}
