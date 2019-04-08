package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
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

        val documentNotation = project.state.documents.values[testPath]!!

        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("Foo")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", project.state.getString(location("Foo"), attribute("hello")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameToSlash() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
C:
  hello: "C"
""")

        val project = NotationAggregate(notation)

        val newName = ObjectName("/")

        project.apply(RenameObjectCommand(
                location("B"), newName))
        val objectPathAsString = "\\/"

        val documentNotation = project.state.documents.values[testPath]!!

        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse(objectPathAsString)).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", project.state.getString(location(objectPathAsString), attribute("hello")))

        assertEquals(location(objectPathAsString),
                project.state.coalesce.locate(ObjectReference(newName, null, null)))
    }
}