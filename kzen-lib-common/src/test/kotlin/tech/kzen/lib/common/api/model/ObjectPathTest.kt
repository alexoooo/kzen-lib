package tech.kzen.lib.common.api.model

import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectPathTest {
    @Test
    fun parseSimpleObjectName() {
        val asString = "foo"
        val literal = ObjectPath(ObjectName("foo"), BundleNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithDot() {
        val asString = "foo.bar"
        val literal = ObjectPath(ObjectName("foo.bar"), BundleNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlash() {
        val asString = "foo\\/bar"
        val literal = ObjectPath(ObjectName("foo/bar"), BundleNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlashInPath() {
        val asString = "foo.hello/bar\\/baz"
        val literal = ObjectPath(
                ObjectName("bar/baz"),
                BundleNesting(listOf(
                        BundleNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofAttribute(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }
}