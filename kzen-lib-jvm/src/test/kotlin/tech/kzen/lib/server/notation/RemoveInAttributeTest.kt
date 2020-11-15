package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.assertTrue


class RemoveInAttributeTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove second directly in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    fizz: 1
    buzz: 1
""")

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.fizz"),
                false)
        )

        val containingMap = transition.graphNotation.firstAttribute(
            location("Foo"), AttributeName("hello")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("1")
        )))
    }


    @Test
    fun `Remove last directly in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    buzz: 1
""")

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.buzz"),
            false)
        )

        val containingMap = transition.graphNotation.directAttribute(
            location("Foo"), AttributeName("hello")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.isEmpty())
    }


    @Test
    fun `Remove last and container directly in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    buzz: 1
""")

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.buzz"),
            true)
        )

        val containingMap = transition.graphNotation.directAttribute(
            location("Foo"), AttributeName("hello")
        ) as? MapAttributeNotation

        assertTrue(containingMap == null)
    }


    @Test
    fun `Remove last and container nested in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    bar:
      baz: x
""")

        val transition = reducer.apply(
            notation,
            RemoveInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.bar.baz"),
            true)
        )

        val containerParentMap = transition.graphNotation.firstAttribute(
            location("Foo"), AttributeName("hello")
        ) as MapAttributeNotation

        assertTrue(containerParentMap.isEmpty())
    }
}