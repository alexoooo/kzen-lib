package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.RelocateObjectTreeRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


// Cross-branch relocate. Reuses refactor-nested-test.yaml — a Holder whose children are an auto-wired
// NestedList (no explicit references between them), structurally identical to Script steps / then / else.
class RelocateObjectTreeRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val nestedPath = DocumentPath.parse("test/refactor-nested-test.yaml")
    private val reducer = NotationReducer()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Relocate leaf into another branch`() {
        val transition = reducer.applySemantic(
            graphDefinition(),
            RelocateObjectTreeRefactorCommand(
                location("main.children/Outer.children/Middle.children/Leaf"),
                ObjectNesting.parse("main.children"),
                PositionRelation.afterLast))

        val objectPaths = transition.graphNotation.documents.map[nestedPath]!!
            .objects.notations.map.keys

        // Leaf re-nested up to main's branch; its old deep path is gone.
        assertEquals(true, objectPaths.contains(ObjectPath.parse("main.children/Leaf")))
        assertEquals(false, objectPaths.contains(
            ObjectPath.parse("main.children/Outer.children/Middle.children/Leaf")))
    }


    @Test
    fun `Relocate subtree preserves descendant nesting and order`() {
        val transition = reducer.applySemantic(
            graphDefinition(),
            RelocateObjectTreeRefactorCommand(
                location("main.children/Outer.children/Middle"),
                ObjectNesting.parse("main.children"),
                PositionRelation.afterLast))

        val documentNotation = transition.graphNotation.documents.map[nestedPath]!!
        val objectPaths = documentNotation.objects.notations.map.keys

        // Middle moved one level up into main's branch, with Leaf still nested under it (grandchild nesting
        // preserved, not flattened to main.children/Leaf).
        assertEquals(true, objectPaths.contains(ObjectPath.parse("main.children/Middle")))
        assertEquals(true, objectPaths.contains(ObjectPath.parse("main.children/Middle.children/Leaf")))
        assertEquals(false, objectPaths.contains(
            ObjectPath.parse("main.children/Outer.children/Middle")))
        // Leaf must NOT be flattened up to main's branch directly.
        assertEquals(false, objectPaths.contains(ObjectPath.parse("main.children/Leaf")))

        // Repositioned to the end (afterLast) as a contiguous block — Leaf immediately follows Middle.
        val middleIndex = documentNotation.indexOf(ObjectPath.parse("main.children/Middle")).value
        val leafIndex = documentNotation.indexOf(ObjectPath.parse("main.children/Middle.children/Leaf")).value
        assertEquals(middleIndex + 1, leafIndex)
        assertEquals(documentNotation.objects.notations.map.size - 1, leafIndex)
    }


    @Test
    fun `Relocate into own subtree is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            reducer.applySemantic(
                graphDefinition(),
                RelocateObjectTreeRefactorCommand(
                    location("main.children/Outer"),
                    // Outer's own descendant branch (Middle.children) — a cycle.
                    ObjectNesting.parse("main.children/Outer.children/Middle.children"),
                    PositionRelation.afterLast))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphDefinition() =
        JvmGraphTestUtils.graphDefinition(JvmGraphTestUtils.readNotation())


    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(
            nestedPath,
            ObjectPath.parse(objectPath))
    }
}
