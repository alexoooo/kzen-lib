package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.server.objects.SelfAware
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.objects.StringHolderRef
import tech.kzen.lib.server.util.GraphTestUtils


class ObjectGraphTest {
    @Test
    fun `ObjectGraph can be empty`() {
        val emptyStructure = GraphStructure(GraphNotation.empty, GraphMetadata.empty)

        val emptyDefinition = GraphDefiner().define(emptyStructure)

        val emptyGraph = GraphCreator().createGraph(
                emptyDefinition)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.size)
    }


    @Test
    fun `Self-aware object should know its name and notation`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val location = location("FooNamed")

        val fooNamedInstance = objectGraph[location]?.reference as SelfAware
        assertEquals(location, fooNamedInstance.objectLocation)
        assertEquals("foo", fooNamedInstance.objectNotation.get(AttributeName("foo"))?.asString())
        assertTrue(location.objectPath in fooNamedInstance.documentNotation.objects.notations.values)
    }


    @Test
    fun `StringHolder can be instantiated`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val helloWorldInstance = objectGraph[location("HelloWorldHolder")]?.reference as StringHolder
        assertEquals("Hello, world!", helloWorldInstance.value)
    }


    @Test
    fun `Numeric message can be used in StringHolder`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val helloWorldInstance = objectGraph[location("NumericStringHolder")]?.reference as StringHolder
        assertEquals("123", helloWorldInstance.value)
    }


    @Test
    fun `Reference can be held to StringHolder`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val refInstance = objectGraph[location("StringHolderRef")]?.reference as StringHolderRef
        assertEquals("Hello, world!", refInstance.stringHolder.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/kzen-test.yaml"),
                ObjectPath.parse(name))
    }
}
