package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectCommand
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class ShiftObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Shift up`() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val transition = reducer.apply(
                notation,
                ShiftObjectCommand(
                        location("B"), PositionIndex(0)))

        val packageNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(0, packageNotation.indexOf(ObjectPath.parse("B")).value)
        assertNotEquals(notation.documents.values[testPath]!!.objects, packageNotation.objects)
    }


    @Test
    fun `Shift down`() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val transition = reducer.apply(
                notation,
                ShiftObjectCommand(
                        location("A"), PositionIndex(1)))

        val packageNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(1, packageNotation.indexOf(ObjectPath.parse("A")).value)
        assertNotEquals(notation.documents.values[testPath]!!.objects, packageNotation.objects)
    }


    @Test
    fun `Shift in place`() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val transition = reducer.apply(
                notation,
                ShiftObjectCommand(
                        location("A"), PositionIndex(0)))

        val packageNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(0, packageNotation.indexOf(ObjectPath.parse("A")).value)
        assertEquals(notation.documents.values[testPath]!!.objects, packageNotation.objects)
    }
}