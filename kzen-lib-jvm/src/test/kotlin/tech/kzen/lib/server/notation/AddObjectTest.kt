package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.edit.AddObjectCommand
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import kotlin.test.assertEquals


class AddObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Add object of parent`() {
        val notation = parseGraph("")

        val project = NotationAggregate(notation)

        project.apply(AddObjectCommand.ofParent(
                location("Foo"),
                PositionIndex(0),
                ObjectName("Parent")
        ))

        val bundleNotation = project.state.bundles.values[testPath]!!
        assertEquals(1, bundleNotation.objects.values.size)

        val objectNotation = bundleNotation.objects.values.values.iterator().next()

        val isValue = (objectNotation.get(NotationConventions.isAttribute) as ScalarAttributeNotation).value as String
        assertEquals("Parent", isValue)

        val deparsedBundle = deparseBundle(bundleNotation)
        assertEquals("""
            Foo:
              is: Parent
        """.trimIndent(), deparsedBundle)
    }
}