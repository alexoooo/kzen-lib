package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.document.DocumentPath
import kotlin.test.Test
import kotlin.test.assertEquals


class DocumentPathTest {
    @Test
    fun parseDocumentPath() {
        val testPath = DocumentPath.parse("hello world/aggregate test.yaml")

        assertEquals("hello world", testPath.nesting.segments.first().value)
        assertEquals("aggregate test.yaml", testPath.name!!.value)
    }
}