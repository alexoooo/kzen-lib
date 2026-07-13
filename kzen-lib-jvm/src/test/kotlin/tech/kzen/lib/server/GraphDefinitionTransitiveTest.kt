package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class GraphDefinitionTransitiveTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val mainPath = DocumentPath.parse("test/digest-main-test.yaml")
    private val otherPath = DocumentPath.parse("test/digest-other-test.yaml")


    private fun document(body: String): DocumentNotation {
        return DocumentNotation(yamlParser.parseDocumentObjects(body), null)
    }


    private fun mainDocument(dependencyValue: String = "1.0"): DocumentNotation {
        return document("""
            Root:
              is: PlusOperation
              addends:
                - Dependency

            Dependency:
              is: DoubleValue
              value: $dependencyValue
        """.trimIndent())
    }


    private fun otherDocument(value: String = "2.0"): DocumentNotation {
        return document("""
            Other:
              is: DoubleValue
              value: $value
        """.trimIndent())
    }


    private fun definition(
        main: DocumentNotation = mainDocument(),
        other: DocumentNotation = otherDocument()
    ): GraphDefinition {
        val notation: GraphNotation = JvmGraphTestUtils
            .readNotation()
            .withNewDocument(mainPath, main)
            .withNewDocument(otherPath, other)

        return JvmGraphTestUtils.graphDefinition(notation).transitiveSuccessful
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Same notation digests equal across independent builds`() {
        // The property definitions lack: two builds of the same notation are never definition-equal
        //  (definer-allocated scaffolding), but their closure content digests are
        assertEquals(
            definition().transitiveDigest(mainPath),
            definition().transitiveDigest(mainPath))
    }


    @Test
    fun `Editing a closure member changes the digest`() {
        assertNotEquals(
            definition().transitiveDigest(mainPath),
            definition(main = mainDocument(dependencyValue = "3.0")).transitiveDigest(mainPath))
    }


    @Test
    fun `Editing an object outside the closure preserves the digest`() {
        assertEquals(
            definition().transitiveDigest(mainPath),
            definition(other = otherDocument(value = "9.0")).transitiveDigest(mainPath))
    }


    @Test
    fun `filterTransitive filters to exactly the transitive closure`() {
        val graphDefinition = definition()
        val documentObjectLocations = graphDefinition
            .objectDefinitions.map.keys.filter { it.documentPath == mainPath }

        assertEquals(
            graphDefinition.transitiveClosure(documentObjectLocations),
            graphDefinition.filterTransitive(mainPath).objectDefinitions.map.keys)
    }
}
