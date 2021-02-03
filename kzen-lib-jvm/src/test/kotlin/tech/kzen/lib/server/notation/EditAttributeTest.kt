package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditAttributeTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Edit simple attribute`() {
        val notation = parseGraph("""
Foo:
  hello: "bar"
""")

        val transition = reducer.apply(
                notation,
                UpsertAttributeCommand(
                        location("Foo"),
                        AttributeName("hello"),
                        ScalarAttributeNotation("world")))

        val value = transition.graphNotation.getString(
                location("Foo"), attribute("hello"))
        assertEquals("world", value)
    }


    @Test
    fun `Edit default attribute`() {
        val notation = parseGraph("""
Foo:
  hello: "bar"
""")

        val transition = reducer.apply(
                notation,
                UpsertAttributeCommand(
                        location("Foo"),
                        AttributeName("foo"),
                        ScalarAttributeNotation("baz")))

        val value = transition.graphNotation.getString(
                location("Foo"), attribute("foo"))
        assertEquals("baz", value)
    }


    @Test
    fun `Edit in map attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    world: bar
""")

        val transition = reducer.apply(
                notation,
                UpdateInAttributeCommand(
                        location("Foo"),
                        AttributePath.parse("hello.world"),
                        ScalarAttributeNotation("baz")))

        val value = transition.graphNotation.getString(
                location("Foo"), attribute("hello.world"))
        assertEquals("baz", value)
    }


    @Test
    fun `Update in list attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    - bar
""")

        val transition = reducer.apply(
                notation,
                UpdateInAttributeCommand(
                        location("Foo"),
                        AttributePath.parse("hello.0"),
                        ScalarAttributeNotation("baz")))

        val value = transition.graphNotation.getString(
                location("Foo"), attribute("hello.0"))
        assertEquals("baz", value)
    }


    @Test
    fun `Update in nested attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    foo:
      bar: baz

Bar:
  is: Foo
  hello: {}
""")

        val transition = reducer.apply(
                notation,
                UpdateInAttributeCommand(
                        location("Bar"),
                        AttributePath.parse("hello.foo.key"),
                        ScalarAttributeNotation("world")))

        val value = transition.graphNotation.getString(
                location("Bar"), attribute("hello.foo.key"))
        assertEquals("world", value)
    }


    @Test
    fun `Shift in list`() {
        val notation = parseGraph("""
Foo:
  hello:
    - bar
    - baz
""")

        val transition = reducer.apply(
                notation,
                ShiftInAttributeCommand(
                    location("Foo"),
                    AttributePath.parse("hello.0"),
                    PositionRelation.at(1)))

        val value = transition.graphNotation.getString(
                location("Foo"), attribute("hello.0"))
        assertEquals("baz", value)
    }


    @Test
    fun `Shift in map`() {
        val notation = parseGraph("""
Foo:
  hello:
    foo: 1
    bar: 2
""")

        val transition = reducer.apply(
            notation,
            ShiftInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.foo"),
                PositionRelation.at(1)))

        val objectNotation =
                transition.graphNotation.coalesce.values[location("Foo")]!!
        val containerNotation =
                objectNotation.get(attribute("hello")) as MapAttributeNotation

        val fooIndex = containerNotation.values.keys.indexOf(AttributeSegment.ofKey("foo"))
        assertEquals(1, fooIndex)
    }


    @Test
    fun `Remove last remaining in list`() {
        val notation = parseGraph("""
Foo:
  hello:
    - baz
  bar: []
""")

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.0"),
                false))

        val newObjectNotation =
                transition.graphNotation.coalesce.values[location("Foo")]!!
        val emptyList =
                newObjectNotation.attributes.values[AttributeName("hello")] as ListAttributeNotation

        assertTrue(emptyList.values.isEmpty())

        assertEquals("""
Foo:
  hello: []
  bar: []
""".trim(), unparseDocument(transition.graphNotation))
    }


    @Test
    fun `Remove last remaining in map`() {
        val notation = parseGraph("""
            Foo:
              hello:
                foo: 1
              bar: {}
            """.trimIndent())

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.foo"),
                false))

        val newObjectNotation =
                transition.graphNotation.coalesce.values[location("Foo")]!!
        val emptyMap =
                newObjectNotation.attributes.values[AttributeName("hello")] as MapAttributeNotation

        assertTrue(emptyMap.values.isEmpty())

        assertEquals("""
            Foo:
              hello: {}
              bar: {}
            """.trimIndent(), unparseDocument(transition.graphNotation))
    }
}