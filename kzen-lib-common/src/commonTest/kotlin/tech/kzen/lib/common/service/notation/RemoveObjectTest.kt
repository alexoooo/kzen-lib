package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import kotlin.test.Test
import kotlin.test.assertEquals


class RemoveObjectTest: StructuralNotationTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove last object`() {
        val notation = parseGraph("""
A:
  hello: "a"
""")

        val transition = reducer.applyStructural(
                notation,
                RemoveObjectCommand(
                        location("A")))

        val packageNotation = transition.graphNotation.documents.map[testPath]!!
        assertEquals(0, packageNotation.objects.notations.map.size)
    }
}