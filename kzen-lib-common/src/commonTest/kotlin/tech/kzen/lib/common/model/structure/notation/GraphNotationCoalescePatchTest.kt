package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame


class GraphNotationCoalescePatchTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val aPath = DocumentPath.parse("a.yaml")
    private val bPath = DocumentPath.parse("b.yaml")
    private val cPath = DocumentPath.parse("c.yaml")


    private fun document(body: String): DocumentNotation {
        return DocumentNotation(yamlParser.parseDocumentObjects(body), null)
    }


    private fun twoDocumentGraph(): GraphNotation {
        return GraphNotation(DocumentPathMap(persistentMapOf(
            aPath to document("""
                A1:
                  hello: a1
                A2:
                  hello: a2
            """.trimIndent()),
            bPath to document("""
                B1:
                  hello: b1
            """.trimIndent()))))
    }


    private fun location(documentPath: DocumentPath, name: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(name))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun patchedCoalescePreservesUntouchedEntriesByIdentity() {
        val notation = twoDocumentGraph()
        val before = notation.coalesce

        val modified = notation.withModifiedDocument(bPath, document("""
            B1:
              hello: modified
        """.trimIndent()))
        val after = modified.coalesce

        for (name in listOf("A1", "A2")) {
            val objectLocation = location(aPath, name)
            assertSame(before.map[objectLocation]!!, after.map[objectLocation]!!)
        }

        val b1 = location(bPath, "B1")
        assertNotEquals(before.map[b1]!!, after.map[b1]!!)
    }


    @Test
    fun patchedModifyMatchesColdFlatten() {
        val notation = twoDocumentGraph()
        notation.coalesce

        val patched = notation.withModifiedDocument(bPath, document("""
            B2:
              hello: b2
        """.trimIndent()))
        val cold = GraphNotation(patched.documents)

        assertEquals(cold.coalesce.map, patched.coalesce.map)
    }


    @Test
    fun patchedNewDocumentMatchesColdFlatten() {
        val notation = twoDocumentGraph()
        notation.coalesce

        val patched = notation.withNewDocument(cPath, document("""
            C1:
              hello: c1
        """.trimIndent()))
        val cold = GraphNotation(patched.documents)

        assertEquals(cold.coalesce.map, patched.coalesce.map)
    }


    @Test
    fun patchedDocumentRemovalMatchesColdFlatten() {
        val notation = twoDocumentGraph()
        notation.coalesce

        val patched = notation.withoutDocument(aPath)
        val cold = GraphNotation(patched.documents)

        assertEquals(cold.coalesce.map, patched.coalesce.map)
    }
}
