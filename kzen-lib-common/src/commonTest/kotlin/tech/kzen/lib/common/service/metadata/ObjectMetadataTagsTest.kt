package tech.kzen.lib.common.service.metadata

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.tag.ObjectTag
import tech.kzen.lib.common.model.structure.metadata.tag.ObjectTagSet
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.util.CommonGraphTestUtils
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ObjectMetadataTagsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = DocumentPath.parse("main.yaml")
    private val yamlParser = YamlNotationParser()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun readSingleObjectTag() {
        val graph = parseGraph("""
Foo:
  meta:
    tags:
      - logic
""")
        val metadata = CommonGraphTestUtils.graphMetadata(graph)

        val foo = metadata.objectMetadata[location("Foo")]!!
        assertEquals(ObjectTagSet.of(ObjectTag("logic")), foo.tags)
    }


    @Test
    fun readMultipleTagsOnOneObject() {
        val graph = parseGraph("""
Foo:
  meta:
    tags:
      - logic
      - hidden
""")
        val metadata = CommonGraphTestUtils.graphMetadata(graph)

        val foo = metadata.objectMetadata[location("Foo")]!!
        assertEquals(ObjectTagSet.of(ObjectTag("logic"), ObjectTag("hidden")), foo.tags)
    }


    @Test
    fun unionTagsAcrossInheritanceChain() {
        val graph = parseGraph("""
Parent:
  meta:
    tags:
      - logic
Child:
  is: Parent
  meta:
    tags:
      - extra
""")
        val metadata = CommonGraphTestUtils.graphMetadata(graph)

        val child = metadata.objectMetadata[location("Child")]!!
        assertEquals(
            ObjectTagSet.of(ObjectTag("logic"), ObjectTag("extra")),
            child.tags)

        val parent = metadata.objectMetadata[location("Parent")]!!
        assertEquals(ObjectTagSet.of(ObjectTag("logic")), parent.tags)
    }


    @Test
    fun absentTagsYieldsEmptySet() {
        val graph = parseGraph("""
Foo:
  bar: baz
""")
        val metadata = CommonGraphTestUtils.graphMetadata(graph)

        val foo = metadata.objectMetadata[location("Foo")]!!
        assertTrue(foo.tags.isEmpty())
        assertEquals(ObjectTagSet.empty, foo.tags)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseGraph(doc: String): GraphNotation {
        val documentNotation = yamlParser.parseDocumentObjects(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
            mainPath to DocumentNotation(documentNotation, null))))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }
}
