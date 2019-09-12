package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertEquals


class AddObjectTest: AggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun addObjectOfParent() {
        val notation = parseGraph("")

        val project = NotationReducer()

        val transition = project.apply(notation, AddObjectCommand.ofParent(
                location("Foo"),
                PositionIndex(0),
                ObjectName("Parent")
        ))

        val documentNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(1, documentNotation.objects.values.size)

        val objectNotation = documentNotation.objects.values.values.iterator().next()

        val isValue = (objectNotation.get(NotationConventions.isAttributePath) as ScalarAttributeNotation).value
        assertEquals("Parent", isValue)

        val unparsedDocument = unparseDocument(documentNotation)
        assertEquals("""
            Foo:
              is: Parent
        """.trimIndent(), unparsedDocument)
    }
}