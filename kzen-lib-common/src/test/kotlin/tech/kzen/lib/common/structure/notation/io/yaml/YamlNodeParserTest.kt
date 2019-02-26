package tech.kzen.lib.common.structure.notation.io.yaml

import tech.kzen.lib.common.structure.notation.format.YamlList
import tech.kzen.lib.common.structure.notation.format.YamlMap
import tech.kzen.lib.common.structure.notation.format.YamlNodeParser
import tech.kzen.lib.common.structure.notation.format.YamlString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class YamlNodeParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun nestedMapWithPound() {
        val node = YamlNodeParser.parse("""
Foo:
  is: "#"
""") as YamlMap

        assertTrue(node.values["Foo"] is YamlMap)
    }


    @Test
    fun nestedMapOfList() {
        val node = YamlNodeParser.parse("""
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
        val node = YamlNodeParser.parse("""
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