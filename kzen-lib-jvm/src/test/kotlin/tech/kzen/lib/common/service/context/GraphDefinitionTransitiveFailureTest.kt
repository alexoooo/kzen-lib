package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * A dangling strong reference defines successfully (graceful degradation) and drops silently out of
 *  transitiveSuccessful. GraphDefinitionAttempt.transitiveFailures is what records why.
 */
class GraphDefinitionTransitiveFailureTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/transitive-failure-test.yaml")

    private val stringHolderAttribute = AttributeName("stringHolder")


    // A declared (rather than value-inferred) attribute type: the reference must survive as a reference even
    //  when it dangles, which is exactly what an archetype's meta gives real notation.
    private val refArchetype = """
        RefArchetype:
          abstract: true
          class: tech.kzen.lib.server.objects.StringHolderRef
          meta:
            stringHolder:
              is: StringHolder
    """.trimIndent()


    private fun attempt(body: String): GraphDefinitionAttempt {
        val notation = JvmGraphTestUtils
            .readNotation()
            .withNewDocument(
                documentPath,
                DocumentNotation(yamlParser.parseDocumentObjects("$refArchetype\n\n$body"), null))

        return JvmGraphTestUtils.graphDefinition(notation)
    }


    private fun location(objectName: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `dangling strong reference names attribute and reference`() {
        val attempt = attempt("""
            Dangling:
              is: RefArchetype
              stringHolder: NoSuchObject
        """.trimIndent())

        val dangling = location("Dangling")

        // graceful degradation: a dangling reference is not a definition failure
        assertNull(attempt.failures[dangling])
        assertTrue(dangling !in attempt.transitiveSuccessful.objectDefinitions)

        val failure = assertNotNull(attempt.transitiveFailures[dangling])

        val attributeFailure = assertNotNull(
            failure.attributeFailures[AttributePath.ofName(stringHolderAttribute)])

        assertEquals(ObjectReference.parse("NoSuchObject"), attributeFailure.unresolvedReference)
        assertEquals(documentPath, attributeFailure.referenceHost?.documentPath)

        val attributeError = assertNotNull(failure.attributeErrors[stringHolderAttribute])
        assertTrue("NoSuchObject" in attributeError, attributeError)
    }


    @Test
    fun `empty required reference is named`() {
        val attempt = attempt("""
            EmptyRef:
              is: RefArchetype
              stringHolder: ""
        """.trimIndent())

        val emptyRef = location("EmptyRef")
        val failure = assertNotNull(attempt.transitiveFailures[emptyRef])

        val attributeFailure = assertNotNull(
            failure.attributeFailures[AttributePath.ofName(stringHolderAttribute)])

        assertTrue(
            "Required reference is empty" in attributeFailure.errorMessage,
            attributeFailure.errorMessage)
    }


    @Test
    fun `derivative drop carries the failed dependency`() {
        val attempt = attempt("""
            Bad:
              is: RefArchetype
              stringHolder: NoSuchObject
            Dependent:
              is: RefArchetype
              stringHolder: Bad
            Healthy:
              is: StringHolder
              value: ok
        """.trimIndent())

        val bad = location("Bad")
        val dependent = location("Dependent")

        val failure = assertNotNull(attempt.transitiveFailures[dependent])
        assertEquals(setOf(bad), failure.missingObjects.values)

        val attributeFailure = assertNotNull(
            failure.attributeFailures[AttributePath.ofName(stringHolderAttribute)])
        assertTrue("failed object" in attributeFailure.errorMessage, attributeFailure.errorMessage)

        // no over-pruning: an unrelated sibling in the same document survives
        assertTrue(location("Healthy") in attempt.transitiveSuccessful.objectDefinitions)
    }
}
