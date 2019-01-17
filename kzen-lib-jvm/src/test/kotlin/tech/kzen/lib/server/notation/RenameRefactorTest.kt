package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.RenameRefactorCommand
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = BundlePath.parse("test/refactor-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references`() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        aggregate.apply(
                RenameRefactorCommand(
                        location("OldName"), ObjectName("NewName")),
                graphDefinition)

        val bundleNotation = aggregate.state.bundleNotations.values[testPath]!!

        assertEquals(1, bundleNotation.indexOf(ObjectPath.parse("NewName")).value)

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