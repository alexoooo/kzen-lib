package tech.kzen.lib.common.service.context

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * A creation failure is attributed per object instead of aborting the whole graph: independent objects still
 *  get created, dependents are skipped with the origin recorded, and createGraph aggregates before throwing.
 */
class GraphCreatorTryCreateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/try-create-test.yaml")

    // Declared (rather than value-inferred) attribute type, so a reference stays a reference even when it dangles
    private val refArchetype = """
        RefArchetype:
          abstract: true
          class: tech.kzen.lib.server.objects.StringHolderRef
          meta:
            stringHolder:
              is: StringHolder
    """.trimIndent()


    private val body = """
        Bad:
          class: tech.kzen.lib.server.objects.ThrowingInit
        Good:
          is: StringHolder
          value: ok
        DependsOnBad:
          is: RefArchetype
          stringHolder: Bad
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
    fun `throwing creator yields failure without aborting the graph`() {
        val definition = attempt(body).transitiveSuccessful

        val instanceAttempt = GraphCreator.tryCreateGraph(definition, JvmGraphTestUtils.testEnvironment)

        val failure = assertNotNull(instanceAttempt.failures[location("Bad")])
        assertTrue("deliberate construction failure" in failure.errorMessage, failure.errorMessage)

        assertNotNull(instanceAttempt.objectInstances[location("Good")])
    }


    @Test
    fun `dependent of a throwing creator is skipped with failedDependencies`() {
        val definition = attempt(body).transitiveSuccessful

        val instanceAttempt = GraphCreator.tryCreateGraph(definition, JvmGraphTestUtils.testEnvironment)

        val failure = assertNotNull(instanceAttempt.failures[location("DependsOnBad")])
        assertEquals(setOf(location("Bad")), failure.failedDependencies)

        assertNull(instanceAttempt.objectInstances[location("DependsOnBad")])
    }


    @Test
    fun `createGraph delegates and throws an aggregate`() {
        val definition = attempt(body).transitiveSuccessful

        val error = assertFailsWith<IllegalStateException> {
            GraphCreator.createGraph(definition, JvmGraphTestUtils.testEnvironment)
        }

        val message = error.message ?: ""
        assertTrue(location("Bad").asString() in message, message)
        assertTrue("deliberate construction failure" in message, message)
    }


    @Test
    fun `unsatisfied reference becomes a per-object failure instead of a throw`() {
        // successful() rather than transitiveSuccessful: the dangling-reference object defines fine, so it
        //  is only the (unpruned) definition that still carries it into creation
        val definition = attempt("""
            Dangling:
              is: RefArchetype
              stringHolder: NoSuchObject
        """.trimIndent()).successful()

        val instanceAttempt = GraphCreator.tryCreateGraph(definition, JvmGraphTestUtils.testEnvironment)

        val dangling = location("Dangling")
        val failure = assertNotNull(instanceAttempt.failures[dangling])

        val unsatisfied = failure.unsatisfiedReferences.map { it.toString() }
        assertTrue(unsatisfied.any { "NoSuchObject" in it && "stringHolder" in it }, "$unsatisfied")
    }
}
