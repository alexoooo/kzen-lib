package tech.kzen.lib.common.notation

import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertEquals


class AddObjectTest: StructuralNotationTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun addObjectOfParent() {
        val notation = parseGraph("")

        val project = NotationReducer()

        val transition = project.applyStructural(notation, AddObjectCommand.ofParent(
                location("Foo"),
                PositionRelation.first,
                ObjectName("Parent")
        ))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!
        assertEquals(1, documentNotation.objects.notations.map.size)

        val objectNotation =
                documentNotation.objects.notations.map.values.iterator().next()

        val isValue = (objectNotation.get(NotationConventions.isAttributePath) as ScalarAttributeNotation).value
        assertEquals("Parent", isValue)

        val unparsedDocument = unparseDocument(documentNotation.objects)
        assertEquals("""
            Foo:
              is: Parent
        """.trimIndent(), unparsedDocument)
    }
}