package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.objects.ast.DoubleExpression
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class GraphCreatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()


    private fun document(body: String): DocumentNotation {
        return DocumentNotation(yamlParser.parseDocumentObjects(body), null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Deep linear dependency chain defines and creates`() {
        // Exercises construction leveling beyond the definer's level cap (which is meta-tower depth,
        //  not object-to-object reference depth — a 20-deep chain must not trip it)
        val chainLength = 20

        val body = buildString {
            appendLine("Chain0:")
            appendLine("  is: DoubleValue")
            appendLine("  value: 1.0")
            for (i in 1 until chainLength) {
                appendLine("Chain$i:")
                appendLine("  is: PlusOperation")
                appendLine("  addends:")
                appendLine("    - Chain${i - 1}")
            }
        }

        val chainPath = DocumentPath.parse("test/chain-test.yaml")
        val notation = JvmGraphTestUtils
            .readNotation()
            .withNewDocument(chainPath, document(body))

        val objectGraph = JvmGraphTestUtils.newObjectGraph(notation)

        val top = objectGraph[ObjectLocation(chainPath, ObjectPath.parse("Chain${chainLength - 1}"))]
        val instance = top?.reference as DoubleExpression
        assertEquals(1.0, instance.evaluate(), 0.0)
    }


    @Test
    fun `Ambiguous reference reports all candidates`() {
        // Host-document filtering disambiguates (or empties) location-hosted references, so create-time
        //  ambiguity is only reachable through global-host resolution — the creator lookup. Shadow a
        //  kzen-base creator name in a user document: the bare creator reference then resolves to two
        //  candidates when located without a host. The shadow's own creator is path-qualified to dodge
        //  the same ambiguity (and a self-edge) on the shadow itself.
        val shadowPath = DocumentPath.parse("test/creator-shadow-test.yaml")

        val body = """
            AttributeObjectCreator:
              class: tech.kzen.lib.common.objects.base.StructuralAttributeDefiner
              creator: "base/kzen-base.yaml#DefaultConstructorObjectCreator"

            Consumer:
              class: tech.kzen.lib.server.objects.StringHolder
              value: consumer
              meta:
                value:
                  is: String
        """.trimIndent()

        val notation = JvmGraphTestUtils
            .readNotation()
            .filterPaths { it == NotationConventions.kzenBasePath }
            .withNewDocument(shadowPath, document(body))

        val attempt = JvmGraphTestUtils.graphDefinition(notation)
        val definition = attempt.successful()

        val error = assertFailsWith<IllegalArgumentException> {
            GraphCreator.createGraph(definition, JvmGraphTestUtils.testEnvironment)
        }

        val message = error.message ?: ""
        assertTrue("Ambiguous reference" in message, message)
        assertTrue("kzen-base" in message, message)
        assertTrue("creator-shadow-test" in message, message)
    }
}
