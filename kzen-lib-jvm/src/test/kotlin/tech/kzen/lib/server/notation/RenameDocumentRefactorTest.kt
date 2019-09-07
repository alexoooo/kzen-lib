package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.edit.RenameDocumentRefactorCommand
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameDocumentRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val newName = DocumentName("new-name")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameDocumentShouldUpdateDocumentPath() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        val originalDocument = aggregate.state.documents.values[testPath]

        aggregate.apply(
                RenameDocumentRefactorCommand(testPath, newName),
                graphDefinition)

        assert(testPath !in aggregate.state.documents.values)

        val newDocumentPath = testPath.withName(newName)

        val documentNotation = aggregate.state.documents.values[newDocumentPath]!!

        assertEquals(originalDocument, documentNotation)
    }
}