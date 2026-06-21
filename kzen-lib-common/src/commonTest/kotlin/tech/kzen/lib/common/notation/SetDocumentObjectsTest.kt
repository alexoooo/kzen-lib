package tech.kzen.lib.common.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.SetDocumentObjectsEvent
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull


class SetDocumentObjectsTest: StructuralNotationTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun replacesAllObjects() {
        val notation = parseGraph("""
A:
  hello: "a"
B:
  hello: "b"
""")

        val replacement = parseDocumentObjects("""
Foo:
  hello: "foo"
""")

        val transition = NotationReducer().applyStructural(
            notation,
            SetDocumentObjectsCommand(testPath, replacement))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(1, documentNotation.objects.notations.map.size)
        assertEquals(0, documentNotation.indexOf(ObjectPath.parse("Foo")).value)
        assertEquals(
            "foo",
            transition.graphNotation.getString(location("Foo"), attribute("hello")))

        val event = transition.notationEvent as SetDocumentObjectsEvent
        assertEquals(testPath, event.documentPath)
        assertEquals(replacement, event.documentObjectNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun replacementWithMainObject() {
        val notation = parseGraph("")

        val replacement = parseDocumentObjects("""
main:
  is: CustomDocument
MyHelper:
  class: java.lang.String
""")

        val transition = NotationReducer().applyStructural(
            notation,
            SetDocumentObjectsCommand(testPath, replacement))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(2, documentNotation.objects.notations.map.size)

        val mainNotation = documentNotation.objects.notations.map[NotationConventions.mainObjectPath]
        assertNotNull(mainNotation)
        val isAttribute = mainNotation.get(NotationConventions.isAttributePath) as ScalarAttributeNotation
        assertEquals("CustomDocument", isAttribute.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preservesResources() {
        val resourcePath = ResourcePath.parse("data.bin")
        val resourceDigest = Digest.ofUtf8("hello")
        val resources = ResourceListing(persistentMapOf(resourcePath to resourceDigest))

        val originalObjects = parseDocumentObjects("""
A:
  hello: "a"
""")

        val notation = GraphNotation(DocumentPathMap(persistentMapOf(
            testPath to DocumentNotation(originalObjects, resources))))

        val replacement = parseDocumentObjects("""
B:
  hello: "b"
""")

        val transition = NotationReducer().applyStructural(
            notation,
            SetDocumentObjectsCommand(testPath, replacement))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(replacement, documentNotation.objects)
        assertEquals(resources, documentNotation.resources)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rejectsUnknownDocumentPath() {
        val notation = parseGraph("""
A:
  hello: "a"
""")

        val replacement = parseDocumentObjects("""
Foo:
  hello: "foo"
""")

        assertFailsWith<IllegalStateException> {
            NotationReducer().applyStructural(
                notation,
                SetDocumentObjectsCommand(DocumentPath.parse("missing.yaml"), replacement))
        }
    }
}
