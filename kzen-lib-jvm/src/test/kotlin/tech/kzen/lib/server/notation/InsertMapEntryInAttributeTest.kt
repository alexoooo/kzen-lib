package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class InsertMapEntryInAttributeTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Prepend directly in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    fizz: 1
""")

        val transition = reducer.apply(
            notation,
            InsertMapEntryInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello"),
                PositionIndex(0),
                AttributeSegment.ofKey("buzz"),
                ScalarAttributeNotation("world"),
                false)
        )

        val containingMap = transition.graphNotation.transitiveAttribute(
            location("Foo"), AttributeName("hello")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("world"),
            AttributeSegment.ofKey("fizz") to ScalarAttributeNotation("1")
        )))
    }


    @Test
    fun `Append directly in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    fizz: 1
""")

        val transition = reducer.apply(
            notation,
            InsertMapEntryInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello"),
                PositionIndex(1),
                AttributeSegment.ofKey("buzz"),
                ScalarAttributeNotation("world"),
                false)
        )

        val containingMap = transition.graphNotation.transitiveAttribute(
            location("Foo"), AttributeName("hello")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("fizz") to ScalarAttributeNotation("1"),
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("world")
        )))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Add first to empty containing map nested in attribute`() {
        val notation = parseGraph("""
Foo:
  hello:
    fizz: {}
""")

        val transition = reducer.apply(
            notation,
            InsertMapEntryInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.fizz"),
                PositionIndex(0),
                AttributeSegment.ofKey("buzz"),
                ScalarAttributeNotation("world"),
                false)
        )

        val containingMap = transition.graphNotation.transitiveAttribute(
            location("Foo"), AttributePath.parse("hello.fizz")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("world")
        )))
    }


    @Test
    fun `Add creating containing map nested in attribute`() {
        val notation = parseGraph("""
Foo:
  hello: {}
""")

        val transition = reducer.apply(
            notation,
            InsertMapEntryInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.fizz"),
                PositionIndex(0),
                AttributeSegment.ofKey("buzz"),
                ScalarAttributeNotation("world"),
                true)
        )

        val containingMap = transition.graphNotation.transitiveAttribute(
            location("Foo"), AttributePath.parse("hello.fizz")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("world")
        )))

        val event = transition.notationEvent as InsertedMapEntryInAttributeEvent
        assertEquals(listOf(AttributePath.parse("hello.fizz")), event.createdAncestors)
    }


    @Test
    fun `Add creating attribute with containing map nested in it`() {
        val notation = parseGraph("""
Foo:
  is: Bar
""")

        val transition = reducer.apply(
            notation,
            InsertMapEntryInAttributeCommand(
                location("Foo"),
                AttributePath.parse("hello.fizz"),
                PositionIndex(0),
                AttributeSegment.ofKey("buzz"),
                ScalarAttributeNotation("world"),
                true)
        )

        val containingMap = transition.graphNotation.transitiveAttribute(
            location("Foo"), AttributePath.parse("hello.fizz")
        ) as MapAttributeNotation

        assertTrue(containingMap.values.equalsInOrder(persistentMapOf(
            AttributeSegment.ofKey("buzz") to ScalarAttributeNotation("world")
        )))

        val event = transition.notationEvent as InsertedMapEntryInAttributeEvent
        assertEquals(listOf(
            AttributePath.parse("hello"),
            AttributePath.parse("hello.fizz")
        ), event.createdAncestors)
    }
}