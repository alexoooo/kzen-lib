package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.document.DocumentPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DocumentPathTest {
    @Test
    fun parseDocumentPath() {
        val testPath = DocumentPath.parse("hello world/aggregate test.yaml")

        assertEquals("hello world", testPath.nesting.segments.first().value)
        assertEquals("aggregate test.yaml", testPath.name!!.value)
    }


    @Test
    fun startsWith() {
        val testPath = DocumentPath.parse("foo/bar.yaml")
        assertEquals("foo/bar.yaml", testPath.asString())

        val prefix = DocumentPath.parse("foo/")
        assertEquals("foo/", prefix.asString())

        assertTrue(testPath.startsWith(prefix), "$testPath should start with $prefix")
    }
}