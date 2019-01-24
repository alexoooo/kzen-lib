package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.server.objects.NameAware
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.util.GraphTestUtils


class ObjectGraphTest {
    @Test
    fun `ObjectGraph can be empty`() {
        val emptyMetadata = GraphMetadata(ObjectMap(mapOf()))

        val emptyDefinition = GraphDefiner.define(
                GraphNotation(BundleTree(mapOf())),
                emptyMetadata)

        val emptyGraph = GraphCreator.createGraph(
                emptyDefinition, emptyMetadata)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.objects.values.size)
    }


    @Test
    fun `Name-aware object should know its name`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val fooNamedInstance = objectGraph.objects.get(location("FooNamed")) as NameAware
        assertEquals(ObjectName("FooNamed"), fooNamedInstance.name)
    }


    @Test
    fun `StringHolder can be instantiated`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val helloWorldInstance = objectGraph.objects.get(location("HelloWorldHolder")) as StringHolder
        assertEquals("Hello, world!", helloWorldInstance.value)
    }


    @Test
    fun `Numeric message can be used in StringHolder`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val helloWorldInstance = objectGraph.objects.get(location("NumericStringHolder")) as StringHolder
        assertEquals("123", helloWorldInstance.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                BundlePath.parse("test/kzen-test.yaml"),
                ObjectPath.parse(name))
    }
}
