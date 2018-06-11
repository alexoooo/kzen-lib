package tech.kzen.lib.common.notation.read.yaml

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseEmptyYaml() {
        val notation = parseProject("")

        assertTrue(notation.coalesce.isEmpty())
    }


    @Test
    fun parseSimpleYaml() {
        val notation = parseProject("""
Foo:
  bar: "baz"
""")

        assertEquals("baz", notation.getString("Foo", "bar"))
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

        assertEquals("hello", notation.getString("Foo", "bar.0"))
        assertEquals("world", notation.getString("Foo", "bar.1"))
        assertEquals("world", notation.getString("Foo", "baz.hello"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun deparseSimpleAddition() {
        val initial = ""

        val expected = "Foo:\n  bar: \"baz\""

        assertEquals(expected, deparse(initial, expected))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parsePackage(doc: String): PackageNotation {
        return yamlParser.parse(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): ProjectNotation {
        val packageNotation = parsePackage(doc)
        return ProjectNotation(mapOf(
                ProjectPath("main.yaml") to packageNotation))
    }


    private fun deparse(initial: String, expected: String): String {
        return IoUtils.utf8ToString(yamlParser.deparse(
                yamlParser.parse(IoUtils.stringToUtf8(expected)),
                IoUtils.stringToUtf8(initial)))
    }
}
