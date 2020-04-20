package tech.kzen.lib.server


import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.objects.NestedObject
import tech.kzen.lib.server.objects.ast.DoubleExpression
import tech.kzen.lib.server.util.GraphTestUtils


class NestedClassTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `nested class can be created`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val nestedLocation = location("Nested")

        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Nested
        assertEquals(42, fooNamedInstance.foo)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/nested-test.yaml"),
                ObjectPath.parse(name))
    }
}
