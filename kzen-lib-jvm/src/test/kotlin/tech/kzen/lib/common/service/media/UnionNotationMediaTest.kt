package tech.kzen.lib.common.service.media

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class UnionNotationMediaTest {
    private fun literal(vararg documents: Pair<String, String>): LiteralNotationMedia {
        return LiteralNotationMedia(DocumentPathMap(documents.associate {
            DocumentPath.parse(it.first) to LiteralNotationMedia.Document(it.second, null)
        }.toPersistentMap()))
    }


    @Test
    fun eachDocumentIsServedByTheMemberThatScannedIt() {
        runBlocking {
            val union = UnionNotationMedia.of(listOf(
                literal("main/a.yaml" to "A: {}", "main/b.yaml" to "B: {}"),
                literal("auto-jvm/c.yaml" to "C: {}")))

            assertEquals(setOf("main/a.yaml", "main/b.yaml", "auto-jvm/c.yaml"),
                union.scan().documents.map.keys.map { it.asString() }.toSet())
            assertEquals("C: {}", union.readDocument(DocumentPath.parse("auto-jvm/c.yaml")))
            assertTrue(union.isReadOnly())
            assertFailsWith<IllegalArgumentException> { union.readDocument(DocumentPath.parse("main/missing.yaml")) }
        }
    }


    @Test
    fun overlappingMembersAreRefusedAtConstruction() {
        runBlocking {
            val failure = assertFailsWith<IllegalArgumentException> {
                UnionNotationMedia.of(listOf(literal("main/a.yaml" to "A: {}"), literal("main/a.yaml" to "A2: {}")))
            }
            assertTrue(failure.message!!.contains("main/a.yaml"), failure.message)
        }
    }
}
