package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.notation.edit.*
import tech.kzen.lib.common.notation.model.ListAttributeNotation
import tech.kzen.lib.common.notation.model.MapAttributeNotation
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
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

        val project = NotationAggregate(notation)

        project.apply(UpsertAttributeCommand(
                location("Foo"),
                AttributeName("hello"),
                ScalarAttributeNotation("world")))

        val value = project.state.getString(location("Foo"), attribute("hello"))
        assertEquals("world", value)
    }


    @Test
    fun `Edit default attribute`() {
        val notation = parseGraph("""
Foo:
  hello: "bar"
""")

        val project = NotationAggregate(notation)

        project.apply(UpsertAttributeCommand(
                location("Foo"),
                AttributeName("foo"),
                ScalarAttributeNotation("baz")))

        val value = project.state.getString(location("Foo"), attribute("foo"))
        assertEquals("baz", value)
    }


    @Test
    fun `Edit in map attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    world: bar
""")

        val project = NotationAggregate(notation)

        project.apply(UpdateInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.world"),
                ScalarAttributeNotation("baz")))

        val value = project.state.getString(location("Foo"), attribute("hello.world"))
        assertEquals("baz", value)
    }


    @Test
    fun `Update in list attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
  - bar
""")

        val project = NotationAggregate(notation)

        project.apply(UpdateInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.0"),
                ScalarAttributeNotation("baz")))

        val value = project.state.getString(location("Foo"), attribute("hello.0"))
        assertEquals("baz", value)
    }


    @Test
    fun `Shift in list`() {
        val notation = parseGraph("""
Foo:
  hello:
  - bar
  - baz
""")

        val project = NotationAggregate(notation)

        project.apply(ShiftInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.0"),
                PositionIndex(1)
        ))

        val value = project.state.getString(location("Foo"), attribute("hello.0"))
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

        val project = NotationAggregate(notation)

        project.apply(ShiftInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.foo"),
                PositionIndex(1)
        ))

        val objectNotation = project.state.coalesce.values[location("Foo")]!!
        val containerNotation = objectNotation.get(attribute("hello")) as MapAttributeNotation

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

        val project = NotationAggregate(notation)

        project.apply(RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.0")
        ))

        val newObjectNotation = project.state.coalesce.values[location("Foo")]!!
        val emptyList = newObjectNotation.attributes[AttributeName("hello")] as ListAttributeNotation

        assertTrue(emptyList.values.isEmpty())

        assertEquals("""
Foo:
  hello: []
  bar: []
""".trim(), deparseBundle(project.state))
    }


    @Test
    fun `Remove last remaining in map`() {
        val notation = parseGraph("""
            Foo:
              hello:
                foo: 1
              bar: {}
            """.trimIndent())

        val project = NotationAggregate(notation)

        project.apply(RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.foo")
        ))

        val newObjectNotation = project.state.coalesce.values[location("Foo")]!!
        val emptyMap = newObjectNotation.attributes[AttributeName("hello")] as MapAttributeNotation

        assertTrue(emptyMap.values.isEmpty())

        assertEquals("""
            Foo:
              hello: {}
              bar: {}
            """.trimIndent(), deparseBundle(project.state))
    }
}