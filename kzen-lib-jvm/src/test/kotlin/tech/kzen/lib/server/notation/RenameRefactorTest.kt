package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.edit.RenameObjectRefactorCommand
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references`() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        aggregate.apply(
                RenameObjectRefactorCommand(
                        location("OldName"), ObjectName("NewName")),
                graphDefinition)

        val documentNotation = aggregate.state.documents.values[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("NewName")).value)

        assertEquals("NewName",
                aggregate.state.getString(location("RefactorObject"),
                        AttributePath.parse("addends.1")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                testPath,
                ObjectPath.parse(name))
    }
}