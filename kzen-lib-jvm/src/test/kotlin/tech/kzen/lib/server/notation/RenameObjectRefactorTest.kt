package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameObjectRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val reducer = NotationReducer()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references`() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.apply(
                graphDefinition,
                RenameObjectRefactorCommand(
                        location("main.addends/OldName"), ObjectName("NewName")))

        val documentNotation = transition.graphNotation.documents.values[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("main.addends/NewName")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("main.addends/SameName")).value)

        assertEquals("main.addends/NewName",
                transition.graphNotation.getString(location("main"),
                        AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename to weird name`() {
        val weirdNameValue = "/"
        val weirdName = ObjectName(weirdNameValue)
        val weirdPath = ObjectPath(weirdName, ObjectNesting.parse("main.addends"))

        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.apply(
                graphDefinition,
                RenameObjectRefactorCommand(
                        location("main.addends/OldName"), weirdName))

        val documentNotation = transition.graphNotation.documents.values[testPath]!!

        assertEquals(location("main.addends/\\/"),
                transition.graphNotation.coalesce.locate(ObjectReference(weirdName, null, null)))

        assertEquals(1, documentNotation.indexOf(weirdPath).value)

        assertEquals(weirdPath.asString(),
                transition.graphNotation.getString(
                        location("main"), AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename container should renamed nested objects`() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.apply(
                graphDefinition,
                RenameObjectRefactorCommand(
                        location("main"), ObjectName("foo")))

        val documentNotation = transition.graphNotation.documents.values[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("foo.addends/OldName")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("foo.addends/SameName")).value)

        assertEquals("foo.addends/OldName",
                transition.graphNotation.getString(location("foo"),
                        AttributePath.parse("addends.0")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(
                testPath,
                ObjectPath.parse(objectPath))
    }
}