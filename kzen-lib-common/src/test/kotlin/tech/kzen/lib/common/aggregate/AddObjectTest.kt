package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.AddObjectCommand
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.test.Test
import kotlin.test.assertEquals


class AddObjectTest: AggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun addObjectOfParent() {
        val notation = parseGraph("")

        val project = NotationAggregate(notation)

        project.apply(AddObjectCommand.ofParent(
                location("Foo"),
                PositionIndex(0),
                ObjectName("Parent")
        ))

        val documentNotation = project.state.documents.values[testPath]!!
        assertEquals(1, documentNotation.objects.values.size)

        val objectNotation = documentNotation.objects.values.values.iterator().next()

        val isValue = (objectNotation.get(NotationConventions.isAttributePath) as ScalarAttributeNotation).value
        assertEquals("Parent", isValue)

        val deparsedDocument = deparseDocument(documentNotation)
        assertEquals("""
            Foo:
              is: Parent
        """.trimIndent(), deparsedDocument)
    }
}