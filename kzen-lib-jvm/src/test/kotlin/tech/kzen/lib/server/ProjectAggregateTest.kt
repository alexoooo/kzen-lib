package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.edit.*
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.common.util.IoUtils
import kotlin.test.assertEquals
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

        project.apply(EditParameterCommand(
                "Foo",
                "hello",
                ScalarParameterNotation("world")))

        val value = project.state.getString("Foo", "hello")
        assertEquals("world", value)
    }


    @Test
    fun `Edit default param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = ProjectAggregate(notation)

        project.apply(EditParameterCommand(
                "Foo",
                "foo",
                ScalarParameterNotation("baz")))

        val value = project.state.getString("Foo", "foo")
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

        project.apply(ShiftObjectCommand(
                "B", 0))

        val packageNotation = project.state.packages[mainPath]!!
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

        project.apply(ShiftObjectCommand(
                "A", 1))

        val packageNotation = project.state.packages[mainPath]!!
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

        project.apply(ShiftObjectCommand(
                "A", 0))

        val packageNotation = project.state.packages[mainPath]!!
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

        project.apply(RenameObjectCommand(
                "B", "Foo"))

        val packageNotation = project.state.packages[mainPath]!!
        assertEquals(0, packageNotation.indexOf("A"))
        assertEquals(1, packageNotation.indexOf("Foo"))
        assertEquals(2, packageNotation.indexOf("C"))
        assertEquals("b", project.state.getString("Foo", "hello"))
    }



    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove last object`() {
        val notation = parseProject("""
A:
  hello: "a"
""")

        val project = ProjectAggregate(notation)

        project.apply(RemoveObjectCommand(
                "A"))

        val packageNotation = project.state.packages[mainPath]!!
        assertEquals(0, packageNotation.objects.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parsePackage(doc: String): PackageNotation {
        return yamlParser.parsePackage(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): ProjectNotation {
        val packageNotation = parsePackage(doc)
        return ProjectNotation(mapOf(
                mainPath to packageNotation))
    }
}