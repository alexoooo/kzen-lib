package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.edit.*
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.IoUtils
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ProjectAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val testPath = BundlePath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Edit simple param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = NotationAggregate(notation)

        project.apply(UpsertAttributeCommand(
                location("Foo"),
                AttributeName("hello"),
                ScalarAttributeNotation("world")))

        val value = project.state.getString(location("Foo"), attribute("hello"))
        assertEquals("world", value)
    }


    @Test
    fun `Edit default param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = NotationAggregate(notation)

        project.apply(UpsertAttributeCommand(
                location("Foo"),
                AttributeName("foo"),
                ScalarAttributeNotation("baz")))

        val value = project.state.getString(location("Foo"), attribute("foo"))
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

        val project = NotationAggregate(notation)

        project.apply(ShiftObjectCommand(
                location("B"), PositionIndex(0)))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.indexOf(ObjectPath.parse("B")).value)
        assertFalse(notation.bundleNotations.values[testPath]!!.objects.equalsInOrder(packageNotation.objects))
    }


    @Test
    fun `Shift down`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val project = NotationAggregate(notation)

        project.apply(ShiftObjectCommand(
                location("A"), PositionIndex(1)))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(1, packageNotation.indexOf(ObjectPath.parse("A")).value)
        assertFalse(notation.bundleNotations.values[testPath]!!.objects.equalsInOrder(packageNotation.objects))
    }


    @Test
    fun `Shift in place`() {
        val notation = parseProject("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val project = NotationAggregate(notation)

        project.apply(ShiftObjectCommand(
                location("A"), PositionIndex(0)))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.indexOf(ObjectPath.parse("A")).value)
        assertTrue(notation.bundleNotations.values[testPath]!!.objects.equalsInOrder(packageNotation.objects))
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

        val project = NotationAggregate(notation)

        project.apply(RenameObjectCommand(
                location("B"), ObjectName("Foo")))

        val bundleNotation = project.state.bundleNotations.values[testPath]!!

        assertEquals(0, bundleNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, bundleNotation.indexOf(ObjectPath.parse("Foo")).value)
        assertEquals(2, bundleNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", project.state.getString(location("Foo"), attribute("hello")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove last object`() {
        val notation = parseProject("""
A:
  hello: "a"
""")

        val project = NotationAggregate(notation)

        project.apply(RemoveObjectCommand(
                location("A")))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create package`() {
        val project = NotationAggregate(NotationTree.empty)

        project.apply(CreateBundleCommand(testPath))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }


    @Test
    fun `Delete package`() {
        val notation = parseProject("")

        val project = NotationAggregate(notation)

        project.apply(DeletePackageCommand(testPath))

        assertTrue(project.state.bundleNotations.values.isEmpty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parsePackage(doc: String): BundleNotation {
        return yamlParser.parseBundle(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): NotationTree {
        val packageNotation = parsePackage(doc)
        return NotationTree(BundleTree(mapOf(
                testPath to packageNotation)))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributeNesting {
        return AttributeNesting.ofAttribute(AttributeName(attribute))
    }
}