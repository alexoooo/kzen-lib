package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteDocumentCommand
import tech.kzen.lib.common.service.notation.NotationAggregate
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditDocumentTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create document`() {
        val project = NotationAggregate(GraphNotation.empty)

        project.apply(CreateDocumentCommand(testPath, DocumentNotation.emptyWithoutResources))

        val documentNotation = project.state.documents.values[testPath]!!
        assertEquals(0, documentNotation.objects.values.size)
    }


    @Test
    fun `Delete document`() {
        val notation = parseGraph("")

        val project = NotationAggregate(notation)

        project.apply(DeleteDocumentCommand(testPath))

        assertTrue(project.state.documents.values.isEmpty())
    }
}