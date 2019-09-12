package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.objects.autowire.ObjectGroup
import tech.kzen.lib.server.objects.autowire.StrongHolder
import tech.kzen.lib.server.objects.autowire.WeakHolder
import tech.kzen.lib.server.util.GraphTestUtils


class AutowiredTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Literal object locations`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("WeakLiteral")
        val weakHolderInstance = objectGraph[location]?.reference as WeakHolder
        
        assertEquals(listOf(
                location("AbstractFoo"),
                location("AbstractBar")
        ), weakHolderInstance.locations)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Autowired object locations`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("WeakHolder")
        val weakHolderInstance = objectGraph[location]?.reference as WeakHolder

        assertEquals(listOf(
                location("AbstractFoo"),
                location("AbstractBar")
        ), weakHolderInstance.locations)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Autowired object instances`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("StrongHolder")
        val strongHolderInstance = objectGraph[location]?.reference as StrongHolder

        assertEquals(2, strongHolderInstance.concreteObjects.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Autowired parent child`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("ObjectGroup")
        val objectGroup = objectGraph.objectInstances[location]?.reference as ObjectGroup

        assertEquals(2, objectGroup.children.size)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/autowired.yaml"),
                ObjectPath.parse(name))
    }
}
