package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import kotlin.test.assertEquals


class ShiftObjectTreeTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    private val nested = """
A:
  hello: "a"
P:
  hello: "p"
P.then/X:
  hello: "x"
P.then/Y:
  hello: "y"
B:
  hello: "b"
"""


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Shift subtree to front`() {
        val notation = parseGraph(nested)

        val transition = reducer.applyStructural(
            notation,
            ShiftObjectTreeCommand(
                location("P"), PositionRelation.first))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!
        // Subtree moved contiguously, internal order preserved, others follow.
        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("P")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("P.then/X")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("P.then/Y")).value)
        assertEquals(3, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(4, documentNotation.indexOf(ObjectPath.parse("B")).value)
    }


    @Test
    fun `Shift subtree to end`() {
        val notation = parseGraph(nested)

        val transition = reducer.applyStructural(
            notation,
            ShiftObjectTreeCommand(
                location("P"), PositionRelation.at(2)))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!
        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("B")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("P")).value)
        assertEquals(3, documentNotation.indexOf(ObjectPath.parse("P.then/X")).value)
        assertEquals(4, documentNotation.indexOf(ObjectPath.parse("P.then/Y")).value)
    }
}
