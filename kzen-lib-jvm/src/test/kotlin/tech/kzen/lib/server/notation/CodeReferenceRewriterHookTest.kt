package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.service.notation.CodeReferenceRewriter
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals


class CodeReferenceRewriterHookTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    // An injected CodeReferenceRewriter's commands are applied as part of the rename refactor transition,
    // alongside the generic typed-reference adjustments.
    @Test
    fun `rename refactor applies injected code reference rewriter commands`() {
        val target = ObjectLocation(testPath, ObjectPath.parse("PartialDivisionDividend"))

        val stub = object: CodeReferenceRewriter {
            override fun renameObjectReferences(
                oldLocation: ObjectLocation,
                newLocation: ObjectLocation,
                graphDefinitionAttempt: GraphDefinitionAttempt
            ): List<UpdateInAttributeCommand> {
                return listOf(UpdateInAttributeCommand(
                    target, AttributePath.parse("dividend"), ScalarAttributeNotation("STUB")))
            }
        }
        val reducer = NotationReducer(listOf(stub))

        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                ObjectLocation(testPath, ObjectPath.parse("main.addends/OldName")), ObjectName("NewName")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!
        val dividend = documentNotation.objects.notations[ObjectPath.parse("PartialDivisionDividend")]!!
            .get(AttributeName("dividend"))?.asString()

        // the stub's command runs after the generic OldName -> NewName adjustment on the same attribute, so it wins
        assertEquals("STUB", dividend)
    }
}
