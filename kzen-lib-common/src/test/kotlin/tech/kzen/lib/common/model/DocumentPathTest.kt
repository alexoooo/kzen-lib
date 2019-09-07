package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DocumentPathTest {
    @Test
    fun parseDocumentPath() {
        val testPath = DocumentPath.parse("hello world/aggregate test.yaml")

        assertEquals("hello world", testPath.nesting.segments.first().value)
        assertEquals("aggregate test", testPath.name.value)
        assertEquals("hello world/aggregate test.yaml", testPath.asString())
    }


    @Test
    fun startsWith() {
        val testPath = DocumentPath.parse("foo/bar.yaml")
        assertEquals("foo/bar.yaml", testPath.asString())

        val prefix = DocumentNesting.parse("foo/")
        assertEquals("foo", prefix.asString())

        assertTrue(testPath.startsWith(prefix), "$testPath should start with $prefix")
    }
}