package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.edit.RenameObjectCommand
import kotlin.test.Test
import kotlin.test.assertEquals


class RenameObjectTest: AggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameBetweenTwoObjects() {
        val notation = parseGraph("""
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

        val bundleNotation = project.state.bundles.values[testPath]!!

        assertEquals(0, bundleNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, bundleNotation.indexOf(ObjectPath.parse("Foo")).value)
        assertEquals(2, bundleNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", project.state.getString(location("Foo"), attribute("hello")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameToUrl() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
C:
  hello: "C"
""")

        val project = NotationAggregate(notation)

        project.apply(RenameObjectCommand(
                location("B"), ObjectName("http://www.yahoo.com/")))
        val objectPathAsString = "http:\\/\\/www.yahoo.com\\/"

        val bundleNotation = project.state.bundles.values[testPath]!!

        assertEquals(0, bundleNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, bundleNotation.indexOf(ObjectPath.parse(objectPathAsString)).value)
        assertEquals(2, bundleNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", project.state.getString(location(objectPathAsString), attribute("hello")))
    }
}