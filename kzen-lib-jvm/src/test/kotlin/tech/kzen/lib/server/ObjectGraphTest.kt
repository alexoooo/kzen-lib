package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.server.objects.*
import tech.kzen.lib.server.objects.custom.CustomDefined
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertNull


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
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("FooNamed")
        val fooNamedInstance = objectGraph[location]?.reference as SelfAware
        assertEquals(location, fooNamedInstance.objectLocation)
        assertEquals("foo", fooNamedInstance.objectNotation.get(AttributeName("foo"))?.asString())
        assertTrue(location.objectPath in fooNamedInstance.documentNotation.objects.notations.map)
    }


    @Test
    fun `StringHolder can be instantiated`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("HelloWorldHolder")
        val helloWorldInstance = objectGraph[location]?.reference as StringHolder
        assertEquals("Hello, world!", helloWorldInstance.value)
    }


    @Test
    fun `Numeric message can be used in StringHolder`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("NumericStringHolder")
        val helloWorldInstance = objectGraph[location]?.reference as StringHolder
        assertEquals("123", helloWorldInstance.value)
    }


    @Test
    fun `Reference can be held to StringHolder`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("StringHolderRef")
        val refInstance = objectGraph[location]?.reference as StringHolderRef
        assertEquals("Hello, world!", refInstance.stringHolder.value)
    }


    @Test
    fun `Nullable reference can be empty`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("StringHolderNullRef")
        val nullableRefInstance = objectGraph[location]?.reference as StringHolderNullableRef
        assertNull(nullableRefInstance.stringHolderOrNull)
    }


    @Test
    fun `Nullable nominal can be empty`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("StringHolderNullableNominal")
        val nullableRefInstance = objectGraph[location]?.reference as StringHolderNullableNominal
        assertNull(nullableRefInstance.stringHolderOrNull)
    }


    @Test
    fun `Escaped attribute name can be used`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("EscapedObject")
        val escapedInstance = objectGraph[location]?.reference as EscapedObject
        assertEquals("Foo", escapedInstance.`else`)
    }


    @Test
    fun `Custom definer is discovered`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("CustomDefined")
        val escapedInstance = objectGraph[location]?.reference as CustomDefined
        assertEquals("foo bar/baz", escapedInstance.customModel.value)
    }


    @Test
    fun `Comments are handled`() {
        val objectGraph = JvmGraphTestUtils.newObjectGraph()

        val location = location("CommentArgObject")
        val escapedInstance = objectGraph[location]?.reference as CommentArgObject
        assertEquals("first", escapedInstance.first)
        assertEquals("fourth", escapedInstance.fourth)
    }


    @Test
    fun `Transitive object merge`() {
        val graphNothing = JvmGraphTestUtils.readNotation()

        val location = location("CommentArgObjectInherit")
        val transitiveObjectLocation = graphNothing.mergeObject(location)

        assertEquals(ObjectNotation(AttributeNameMap(persistentMapOf(
            AttributeName("first") to ScalarAttributeNotation("foo"),
            AttributeName("fourth") to ScalarAttributeNotation("fourth")
        ))), transitiveObjectLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/kzen-test.yaml"),
                ObjectPath.parse(name))
    }
}
