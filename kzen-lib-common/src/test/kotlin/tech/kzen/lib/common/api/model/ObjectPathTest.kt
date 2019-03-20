package tech.kzen.lib.common.api.model

import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectPathTest {
    @Test
    fun parseSimpleObjectName() {
        val asString = "foo"
        val literal = ObjectPath(ObjectName("foo"), DocumentNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithDot() {
        val asString = "foo.bar"
        val literal = ObjectPath(ObjectName("foo.bar"), DocumentNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlash() {
        val asString = "foo\\/bar"
        val literal = ObjectPath(ObjectName("foo/bar"), DocumentNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlashInPath() {
        val asString = "foo.hello/bar\\/baz"
        val literal = ObjectPath(
                ObjectName("bar/baz"),
                DocumentNesting(listOf(
                        DocumentNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofAttribute(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithUrlName() {
        val asString = "foo.hello/http:\\/\\/www.google.com\\/"
        val literal = ObjectPath(
                ObjectName("http://www.google.com/"),
                DocumentNesting(listOf(
                        DocumentNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofAttribute(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }
}