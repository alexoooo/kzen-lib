package tech.kzen.lib.server


import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.objects.nested.NestedObject
import tech.kzen.lib.server.util.GraphTestUtils


class NestedClassTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `nested class can be created`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val nestedLocation = location("Nested")

        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Nested
        assertEquals(42, fooNamedInstance.foo())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `nested user can be created`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val nestedLocation = location("NestedUser")

        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Foo
        assertEquals(42, fooNamedInstance.foo())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `generic int parameter in nested object`() {
        val objectGraph = GraphTestUtils.newObjectGraph()

        val nestedLocation = location("Nested2")

        @Suppress("UNCHECKED_CAST")
        val fooNamedInstance = objectGraph[nestedLocation]?.reference as NestedObject.Nested2<Int>
        assertEquals(listOf(11, 22), fooNamedInstance.foo)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Test
//    fun `unusual parse class`() {
//        val constructorReflection = ModuleReflectionGenerator.reflectConstructors(
//                mapOf(
//                    Path.of("tech/kzen/auto/client/objects/document/graph/DefaultConstructorObjectCreator.kt") to
//"""
//package tech.kzen.lib.common.objects.bootstrap
//
//import tech.kzen.lib.common.api.ObjectCreator
//import tech.kzen.lib.common.model.attribute.AttributeNameMap
//import tech.kzen.lib.common.model.definition.ObjectDefinition
//import tech.kzen.lib.common.model.instance.GraphInstance
//import tech.kzen.lib.common.model.instance.ObjectInstance
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.GraphStructure
//import tech.kzen.lib.common.reflect.GlobalMirror
//import tech.kzen.lib.common.reflect.Reflect
//
//
//@Reflect
//object DefaultConstructorObjectCreator: ObjectCreator {
//""".trimIndent()
//        ))
//
//        assertEquals("stepDisplays", constructorReflection.values.first().arguments[0].name)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/nested-test.yaml"),
                ObjectPath.parse(name))
    }
}
