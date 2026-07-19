package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals


class YamlCommentTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun commentAttachesToMapEntry() {
        val node = YamlParser.parse("""
# comment for a
a: 1
""") as YamlMap
        assertEquals(listOf("comment for a"), node.values["a"]!!.comments)
    }


    @Test
    fun commentAttachesToListItem() {
        val node = YamlParser.parse("""
# item comment
- x
""") as YamlList
        assertEquals(listOf("item comment"), node.values.single().comments)
        assertEquals("x", node.values.single().toObject())
    }


    @Test
    fun commentAttachesToNestedEntry() {
        val node = YamlParser.parse("""
a:
  # comment for b
  b: 1
""") as YamlMap
        val inner = node.values["a"] as YamlMap
        assertEquals(listOf("comment for b"), inner.values["b"]!!.comments)
    }


    @Test
    fun dedentBoundaryAttachesOutward() {
        val node = YamlParser.parse("""
a:
  b: 1
# comment for c
c: 2
""") as YamlMap
        val inner = node.values["a"] as YamlMap
        assertEquals(listOf<String>(), inner.values["b"]!!.comments)
        assertEquals(listOf("comment for c"), node.values["c"]!!.comments)
    }


    @Test
    fun multipleCommentLinesAttach() {
        val node = YamlParser.parse("""
# first
# second
a: 1
""") as YamlMap
        assertEquals(listOf("first", "second"), node.values["a"]!!.comments)
    }


    @Test
    fun bannerCommentTextPreserved() {
        val node = YamlParser.parse("""
####
a: 1
""") as YamlMap
        assertEquals(listOf("###"), node.values["a"]!!.comments)
    }


    @Test
    fun trailingCommentsDiscarded() {
        val node = YamlParser.parse("""
a: 1
# trailing
""") as YamlMap
        assertEquals(listOf<String>(), node.values["a"]!!.comments)
        assertEquals(1, node.values.size)
    }


    @Test
    fun keyWithOnlyCommentHasEmptyValue() {
        val node = YamlParser.parse("key: # c") as YamlMap
        assertEquals("", node.values["key"]?.toObject())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unparseEntryComment() {
        val node = YamlMap(mapOf("a" to YamlString("1", listOf("hello"))))
        assertEquals("# hello\na: 1", YamlParser.unparse(node))
    }


    @Test
    fun unparseBannerComment() {
        val node = YamlMap(mapOf("a" to YamlString("1", listOf("###"))))
        assertEquals("####\na: 1", YamlParser.unparse(node))
    }


    @Test
    fun commentedTreeRoundTrips() {
        val original = YamlMap(mapOf(
            "a" to YamlString("1", listOf("comment for a")),
            "b" to YamlString("2")))
        val roundTrip = YamlParser.parse(YamlParser.unparse(original))
        assertEquals(original, roundTrip)
    }
}
