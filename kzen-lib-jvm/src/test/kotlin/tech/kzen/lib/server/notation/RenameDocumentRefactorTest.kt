package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.DocumentName
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.edit.RenameDocumentRefactorCommand
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameDocumentRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val newSegment = DocumentName("new-name.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameDocumentShouldUpdateDocumentPath() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        val originalDocument = aggregate.state.documents.values[testPath]

        aggregate.apply(
                RenameDocumentRefactorCommand(
                        testPath,
                        newSegment),
                graphDefinition)

        assert(testPath !in aggregate.state.documents.values)

        val newDocumentPath = DocumentPath(
                testPath
                        .segments
                        .subList(0, testPath.segments.size - 1)
                        .plus(newSegment)
        )

        val documentNotation = aggregate.state.documents.values[newDocumentPath]!!

        assertEquals(originalDocument, documentNotation)
    }
}