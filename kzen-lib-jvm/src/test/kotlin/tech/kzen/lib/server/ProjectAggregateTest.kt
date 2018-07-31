package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.edit.EditParameterCommand
import tech.kzen.lib.common.edit.ProjectAggregate
import tech.kzen.lib.common.edit.RenameObjectCommand
import tech.kzen.lib.common.edit.ShiftObjectCommand
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.common.util.IoUtils
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ProjectAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val mainPath = ProjectPath("main.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Edit simple param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(EditParameterCommand(
                "Foo",
                "hello",
                ScalarParameterNotation("world")))

        val value = event.state.getString("Foo", "hello")
        assertEquals("world", value)
    }


    @Test
    fun `Edit default param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(EditParameterCommand(
                "Foo",
                "foo",
                ScalarParameterNotation("baz")))

        val value = event.state.getString("Foo", "foo")
        assertEquals("baz", value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Shift up`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(ShiftObjectCommand(
                "B", 0))

        val packageNotation = event.state.packages[mainPath]!!
        assertEquals(0, packageNotation.indexOf("B"))
        assertFalse(notation.packages[mainPath]!!.equalsInOrder(packageNotation))
    }


    @Test
    fun `Shift down`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(ShiftObjectCommand(
                "A", 1))

        val packageNotation = event.state.packages[mainPath]!!
        assertEquals(1, packageNotation.indexOf("A"))
        assertFalse(notation.packages[mainPath]!!.equalsInOrder(packageNotation))
    }


    @Test
    fun `Shift in place`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(ShiftObjectCommand(
                "A", 0))

        val packageNotation = event.state.packages[mainPath]!!
        assertEquals(0, packageNotation.indexOf("A"))
        assertTrue(notation.packages[mainPath]!!.equalsInOrder(packageNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename between two objects`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
C:
  hello: "C"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(RenameObjectCommand(
                "B", "Foo"))

        val packageNotation = event.state.packages[mainPath]!!
        assertEquals(0, packageNotation.indexOf("A"))
        assertEquals(1, packageNotation.indexOf("Foo"))
        assertEquals(2, packageNotation.indexOf("C"))
        assertEquals("b", event.state.getString("Foo", "hello"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parsePackage(doc: String): PackageNotation {
        return yamlParser.parse(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): ProjectNotation {
        val packageNotation = parsePackage(doc)
        return ProjectNotation(mapOf(
                mainPath to packageNotation))
    }
}