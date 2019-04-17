package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.server.objects.LocationAware
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.objects.StringHolderRef
import tech.kzen.lib.server.util.GraphTestUtils


class ObjectGraphTest {
    @Test
    fun `ObjectGraph can be empty`() {
        val emptyStructure = GraphStructure(GraphNotation.empty, GraphMetadata.empty)

        val emptyDefinition = GraphDefiner.define(emptyStructure)

        val emptyGraph = GraphCreator.createGraph(
                emptyStructure, emptyDefinition)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.objects.values.size)
    }


    @Test
    fun `Name-aware object should know its name`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("FooNamed")
        val fooNamedInstance = objectGraph.objects.get(location) as LocationAware
        assertEquals(location, fooNamedInstance.objectLocation)
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


    @Test
    fun `Reference can be held to StringHolder`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val refInstance = objectGraph.objects.get(location("StringHolderRef")) as StringHolderRef
        assertEquals("Hello, world!", refInstance.stringHolder.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/kzen-test.yaml"),
                ObjectPath.parse(name))
    }
}
