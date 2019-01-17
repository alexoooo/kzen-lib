package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.ShiftObjectCommand
import tech.kzen.lib.common.notation.model.PositionIndex
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ShiftObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Shift up`() {
        val notation = parseTree("""
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
        val notation = parseTree("""
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
        val notation = parseTree("""
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
}