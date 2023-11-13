package tech.kzen.lib.server


import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.objects.ast.DoubleExpression
import tech.kzen.lib.server.objects.ast.PlusOperationNamed
import tech.kzen.lib.server.objects.ast.PlusOperationNamedNominal
import tech.kzen.lib.server.util.JvmGraphTestUtils


class AstGraphTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Literal 2 + 2 = 4`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()
        val location = location("TwoPlusTwo")
        val instance = objectGraph[location]?.reference as DoubleExpression
        assertEquals(4.0, instance.evaluate(), 0.0)
    }


    @Test
    fun `Inline 2 + 2 = 4`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()
        val location = location("TwoPlusTwoInline")
        val instance = objectGraph[location]?.reference as DoubleExpression
        assertEquals(4.0, instance.evaluate(), 0.0)
    }


    @Test
    fun `Named 2 + 2 = 4`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()
        val location = location("TwoPlusTwoNamed")
        val instance = objectGraph[location]?.reference as PlusOperationNamed
        assertEquals(listOf("foo", "bar"), instance.addends.keys.toList())
        assertEquals(4.0, instance.evaluate(), 0.0)
    }


    @Test
    fun `Named nominal 2 + 2`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()
        val location = location("TwoPlusTwoNamedNominal")
        val instance = objectGraph[location]?.reference as PlusOperationNamedNominal
        assertEquals(listOf("foo", "bar"), instance.addends.keys.toList())
        assertEquals(listOf("Two", "Two"), instance.addends.values.map { it.objectPath.name.value })
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/nested-test.yaml"),
                ObjectPath.parse(name))
    }
}
