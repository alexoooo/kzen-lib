package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.MoveFolderRefactorCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameFolderRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class RenameFolderRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    // The relocated folder and its contents (a markerless folder containing two mutually/externally referenced docs).
    private val folderPath = DocumentPath.parse("test/relocate/")
    private val insideA = DocumentPath.parse("test/relocate/inside-a.yaml")
    private val insideB = DocumentPath.parse("test/relocate/inside-b.yaml")
    private val outside = DocumentPath.parse("test/relocate-outside.yaml")

    private val reducer = NotationReducer


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun renameFolderReNestsContentsAndRewritesReferences() {
        val graphDefinition = JvmGraphTestUtils.graphDefinition(notationWithFolder())

        val transition = reducer.applySemantic(
            graphDefinition,
            RenameFolderRefactorCommand(folderPath, DocumentName("renamed")))

        val result = transition.graphNotation
        val map = result.documents.map

        // old subtree gone, new subtree present (folder entry included)
        assertFalse(folderPath in map)
        assertFalse(insideA in map)
        assertFalse(insideB in map)
        assertTrue(DocumentPath.parse("test/renamed/") in map)

        val newInsideA = DocumentPath.parse("test/renamed/inside-a.yaml")
        val newInsideB = DocumentPath.parse("test/renamed/inside-b.yaml")
        assertTrue(newInsideA in map)
        assertTrue(newInsideB in map)

        // inside -> inside reference rewritten on the moved copy
        assertEquals("test/renamed/inside-b.yaml#Main", reference(result, newInsideA, "Main", "dividend"))
        // outside -> inside reference rewritten in place
        assertEquals("test/renamed/inside-b.yaml#Main", reference(result, outside, "OutRef", "dividend"))
    }


    @Test
    fun moveFolderReNestsContentsUnderDestination() {
        // add an empty destination folder and move test/relocate/ under it -> test/dest/relocate/
        val withDestination = notationWithFolder().let {
            val builder = it.documents.map.toMutableMap()
            builder[DocumentPath.parse("test/dest/")] = DocumentNotation.folder
            GraphNotation(DocumentPathMap(builder.toPersistentMap()))
        }
        val graphDefinition = JvmGraphTestUtils.graphDefinition(withDestination)

        val transition = reducer.applySemantic(
            graphDefinition,
            MoveFolderRefactorCommand(folderPath, DocumentNesting.parse("test/dest")))

        val result = transition.graphNotation
        val map = result.documents.map

        assertFalse(folderPath in map)
        assertTrue(DocumentPath.parse("test/dest/relocate/") in map)

        val movedInsideA = DocumentPath.parse("test/dest/relocate/inside-a.yaml")
        assertTrue(movedInsideA in map)
        assertTrue(DocumentPath.parse("test/dest/relocate/inside-b.yaml") in map)

        assertEquals(
            "test/dest/relocate/inside-b.yaml#Main",
            reference(result, movedInsideA, "Main", "dividend"))
        assertEquals(
            "test/dest/relocate/inside-b.yaml#Main",
            reference(result, outside, "OutRef", "dividend"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Augments the shared test notation in-memory (not on disk, so the shared readNotation() stays unaffected) with a
    // folder of cross-referencing documents. Archetypes (DivideOperation, DoubleValue) come from the loaded fixtures.
    private fun notationWithFolder(): GraphNotation {
        val parser = YamlNotationParser()
        val base = JvmGraphTestUtils.readNotation()
        val builder = base.documents.map.toMutableMap()

        builder[folderPath] = DocumentNotation.folder

        builder[insideA] = DocumentNotation(
            parser.parseDocumentObjects("""
                Main:
                  is: DivideOperation
                  dividend: "test/relocate/inside-b.yaml#Main"
                  divisor: "test/relocate/inside-b.yaml#Main"
            """.trimIndent()),
            null)

        builder[insideB] = DocumentNotation(
            parser.parseDocumentObjects("""
                Main:
                  is: DoubleValue
                  value: 2
            """.trimIndent()),
            null)

        builder[outside] = DocumentNotation(
            parser.parseDocumentObjects("""
                OutRef:
                  is: DivideOperation
                  dividend: "test/relocate/inside-b.yaml#Main"
                  divisor: "test/relocate/inside-b.yaml#Main"
            """.trimIndent()),
            null)

        return GraphNotation(DocumentPathMap(builder.toPersistentMap()))
    }


    private fun reference(
        notation: GraphNotation,
        documentPath: DocumentPath,
        objectName: String,
        attribute: String
    ): String {
        val objectNotation = notation.documents.map[documentPath]!!
            .objects.notations.map[ObjectPath.parse(objectName)]!!
        val scalar = objectNotation.get(AttributePath.parse(attribute)) as ScalarAttributeNotation
        return scalar.value
    }
}
