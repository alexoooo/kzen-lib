package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectNestingSegment
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectPathTest {
    @Test
    fun parseSimpleObjectName() {
        val asString = "foo"
        val literal = ObjectPath(ObjectName("foo"), ObjectNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithDot() {
        val asString = "foo.bar"
        val literal = ObjectPath(ObjectName("foo.bar"), ObjectNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlash() {
        val asString = "foo\\/bar"
        val literal = ObjectPath(ObjectName("foo/bar"), ObjectNesting.root)
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithSlashInPath() {
        val asString = "foo.hello/bar\\/baz"
        val literal = ObjectPath(
                ObjectName("bar/baz"),
                ObjectNesting(persistentListOf(
                        ObjectNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofName(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameWithUrl() {
        val asString = "foo.hello/http:\\/\\/www.google.com\\/"
        val literal = ObjectPath(
                ObjectName("http://www.google.com/"),
                ObjectNesting(persistentListOf(
                        ObjectNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofName(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }


    @Test
    fun parseNameSingle() {
        val asString = "foo.hello/http:\\/\\/www.google.com\\/"
        val literal = ObjectPath(
                ObjectName("http://www.google.com/"),
                ObjectNesting(persistentListOf(
                        ObjectNestingSegment(
                                ObjectName("foo"),
                                AttributePath.ofName(AttributeName("hello"))
                        ))))
        assertEquals(literal, ObjectPath.parse(asString))
        assertEquals(asString, literal.asString())
    }
}