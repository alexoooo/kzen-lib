package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteDocumentCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditDocumentTest: StructuralNotationTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create document`() {
        val transition = reducer.applyStructural(
                GraphNotation.empty,
                CreateDocumentCommand(testPath, DocumentObjectNotation.empty))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!
        assertEquals(0, documentNotation.objects.notations.map.size)
    }


    @Test
    fun `Delete document`() {
        val notation = parseGraph("")

        val transition = reducer.applyStructural(
                notation, DeleteDocumentCommand(testPath))

        assertTrue(transition.graphNotation.documents.map.isEmpty())
    }
}