package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import kotlin.test.assertEquals


class RemoveObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove last object`() {
        val notation = parseGraph("""
A:
  hello: "a"
""")

        val transition = reducer.apply(
                notation,
                RemoveObjectCommand(
                        location("A")))

        val packageNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }
}