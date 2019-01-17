package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.RemoveObjectCommand
import kotlin.test.assertEquals


class RemoveObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Remove last object`() {
        val notation = parseTree("""
A:
  hello: "a"
""")

        val project = NotationAggregate(notation)

        project.apply(RemoveObjectCommand(
                location("A")))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }
}