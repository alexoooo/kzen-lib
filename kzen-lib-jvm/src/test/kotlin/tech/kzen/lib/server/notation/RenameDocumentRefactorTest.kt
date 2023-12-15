package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals


class RenameDocumentRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val newName = DocumentName("new-name")
    private val reducer = NotationReducer()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameDocumentShouldUpdateDocumentPath() {
        val graphNotation = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(graphNotation)

        val originalDocument = graphNotation.documents.map[testPath]

        val transition= reducer.applySemantic(
                graphDefinitionAttempt,
                RenameDocumentRefactorCommand(testPath, newName))

        assert(testPath !in transition.graphNotation.documents.map)

        val newDocumentPath = testPath.withName(newName)

        val documentNotation = transition.graphNotation.documents.map[newDocumentPath]!!

        assertEquals(originalDocument, documentNotation)
    }
}