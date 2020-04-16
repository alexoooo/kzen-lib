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
}