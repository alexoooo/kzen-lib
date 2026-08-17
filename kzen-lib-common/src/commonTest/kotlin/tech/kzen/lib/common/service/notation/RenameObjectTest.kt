package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectCommand
import kotlin.test.Test
import kotlin.test.assertEquals


class RenameObjectTest: StructuralNotationTest() {
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

        val transition = reducer.applyStructural(
                notation,
                RenameObjectCommand(
                        location("B"), ObjectName("Foo")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("Foo")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", transition.graphNotation.getString(
                location("Foo"), attribute("hello")))
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

        val newName = ObjectName("/")

        val transition = reducer.applyStructural(
                notation,
                RenameObjectCommand(
                location("B"), newName))

        val objectPathAsString = "\\/"

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse(objectPathAsString)).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("C")).value)
        assertEquals("b", transition.graphNotation.getString(location(objectPathAsString), attribute("hello")))

        assertEquals(location(objectPathAsString),
                transition.graphNotation.coalesce.locate(ObjectReference.ofRootName(newName)))
    }
}