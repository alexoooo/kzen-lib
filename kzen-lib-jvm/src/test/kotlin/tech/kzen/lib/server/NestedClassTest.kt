package tech.kzen.lib.server


import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.objects.nested.NestedObject
import tech.kzen.lib.server.util.JvmGraphTestUtils


class NestedClassTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `nested class can be created`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val nestedLocation = location("Nested")

        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Nested
        assertEquals(42, fooNamedInstance.foo())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `nested user can be created`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val nestedLocation = location("NestedUser")

        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Foo
        assertEquals(42, fooNamedInstance.foo())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `generic int parameter in nested object`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val nestedLocation = location("Nested2")

        @Suppress("UNCHECKED_CAST")
        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Nested2<Int>
        assertEquals(listOf(11, 22), fooNamedInstance.foo)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/nested-test.yaml"),
                ObjectPath.parse(name))
    }
}
