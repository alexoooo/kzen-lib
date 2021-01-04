package tech.kzen.lib.common.util.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlParseTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun nestedMapWithPound() {
        val node = YamlParser.parse("""
Foo:
  is: "#"
""") as YamlMap

        assertTrue(node.values["Foo"] is YamlMap)
    }


    @Test
    fun nestedMapOfList() {
        val node = YamlParser.parse("""
Foo:
  - "bar"
  - "baz"
""") as YamlMap

        assertTrue(node.values["Foo"] is YamlList)
        assertTrue((node.values["Foo"] as YamlList).values[0] is YamlString)
        assertEquals("bar", ((node.values["Foo"] as YamlList).values[0] as YamlString).value)
        assertEquals("baz", ((node.values["Foo"] as YamlList).values[1] as YamlString).value)
    }


    @Test
    fun nestedMapOfListInlineIndent() {
        val node = YamlParser.parse("""
Foo:
- "bar"
- "baz"
""") as YamlMap

        assertTrue(node.values["Foo"] is YamlList)
        assertTrue((node.values["Foo"] as YamlList).values[0] is YamlString)
        assertEquals("bar", ((node.values["Foo"] as YamlList).values[0] as YamlString).value)
        assertEquals("baz", ((node.values["Foo"] as YamlList).values[1] as YamlString).value)
    }


    @Test
    fun mapOfListWithSpecialCharacters() {
        val node = YamlParser.parse("""
Foo/bar baz:
  - foo/bar
  - hello world
""") as YamlMap

        assertTrue(node.values["Foo/bar baz"] is YamlList)
        assertTrue((node.values["Foo/bar baz"] as YamlList).values[0] is YamlString)
        assertEquals("foo/bar", ((node.values["Foo/bar baz"] as YamlList).values[0] as YamlString).value)
        assertEquals("hello world", ((node.values["Foo/bar baz"] as YamlList).values[1] as YamlString).value)
    }


    @Test
    fun listOfMap() {
        val node = YamlParser.parse("""
- foo: bar
  hello: world
""") as YamlList

        assertTrue(node.values.single() is YamlMap)
        assertEquals(2, (node.values[0] as YamlMap).values.size)
        assertEquals("bar", (node.values[0] as YamlMap).values["foo"]?.toObject())
        assertEquals("world", (node.values[0] as YamlMap).values["hello"]?.toObject())
    }


    @Test
    fun stringWithComment() {
        val node = YamlParser.parse("""
foo: "foo" 
bar: "bar"######
baz: "baz"  # baz

FOO: 'foo' 
BAR: 'bar'######
BAZ: 'baz'  # baz
""") as YamlMap

        assertEquals("foo", node.values["foo"]?.toObject())
        assertEquals("bar", node.values["bar"]?.toObject())
        assertEquals("baz", node.values["baz"]?.toObject())

        assertEquals("foo", node.values["FOO"]?.toObject())
        assertEquals("bar", node.values["BAR"]?.toObject())
        assertEquals("baz", node.values["BAZ"]?.toObject())
    }
}