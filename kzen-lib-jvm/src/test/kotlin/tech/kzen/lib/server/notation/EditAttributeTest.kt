package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.UpdateInAttributeCommand
import tech.kzen.lib.common.notation.edit.UpsertAttributeCommand
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import kotlin.test.assertEquals


class EditAttributeTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Edit simple attribute`() {
        val notation = parseTree("""
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
        val notation = parseTree("""
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
        val notation = parseTree("""
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
    fun `Edit in list attribute`() {
        val notation = parseTree("""
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
}