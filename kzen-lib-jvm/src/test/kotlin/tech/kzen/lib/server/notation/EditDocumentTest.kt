package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteDocumentCommand
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditDocumentTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create document`() {
        val transition = reducer.apply(
                GraphNotation.empty,
                CreateDocumentCommand(testPath, DocumentNotation.emptyWithoutResources))

        val documentNotation = transition.graphNotation.documents.values[testPath]!!
        assertEquals(0, documentNotation.objects.values.size)
    }


    @Test
    fun `Delete document`() {
        val notation = parseGraph("")

        val transition = reducer.apply(
                notation, DeleteDocumentCommand(testPath))

        assertTrue(transition.graphNotation.documents.values.isEmpty())
    }
}