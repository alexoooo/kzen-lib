package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.server.objects.autowire.WeakHolder
import tech.kzen.lib.server.util.GraphTestUtils


class AutowiredTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Autowired object locations`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("WeakHolder")
        val weakHolderInstance = objectGraph.objects.get(location) as WeakHolder

        assertEquals(listOf(
                location("AbstractFoo"),
                location("AbstractBar")
        ), weakHolderInstance.locations)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                BundlePath.parse("test/autowired.yaml"),
                ObjectPath.parse(name))
    }
}
