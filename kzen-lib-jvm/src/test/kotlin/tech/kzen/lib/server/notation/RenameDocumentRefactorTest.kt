package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameDocumentRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val newName = DocumentName("new-name")
    private val reducer = NotationReducer()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameDocumentShouldUpdateDocumentPath() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.graphDefinition(notationTree)

        val originalDocument = notationTree.documents.values[testPath]

        val transition= reducer.apply(
                graphDefinition,
                RenameDocumentRefactorCommand(testPath, newName))

        assert(testPath !in transition.graphNotation.documents.values)

        val newDocumentPath = testPath.withName(newName)

        val documentNotation = transition.graphNotation.documents.values[newDocumentPath]!!

        assertEquals(originalDocument, documentNotation)
    }
}