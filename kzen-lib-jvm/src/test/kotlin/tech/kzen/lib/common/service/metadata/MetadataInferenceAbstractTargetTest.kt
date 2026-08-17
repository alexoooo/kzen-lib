package tech.kzen.lib.common.service.metadata

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * NotationMetadataReader.inferMetadata promotes an undeclared scalar attribute whose value resolves to a
 *  graph object into a synthesized `is:` attribute — but only when the target is concrete. An inferred hard
 *  reference to an `abstract: true` object could never define (abstract objects are excluded from definition),
 *  so promotion would just get the host silently pruned by transitiveSuccessful; such scalars stay raw data.
 */
class MetadataInferenceAbstractTargetTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/inference-abstract-target-test.yaml")

    private val extraAttribute = AttributeName("extra")


    private fun notation(body: String): GraphNotation {
        return JvmGraphTestUtils
            .readNotation()
            .withNewDocument(
                documentPath,
                DocumentNotation(yamlParser.parseDocumentObjects(body), null))
    }


    private fun location(objectName: String): ObjectLocation {
        return ObjectLocation(documentPath, ObjectPath.parse(objectName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `undeclared scalar naming a concrete object is inferred as a reference`() {
        val graphNotation = notation("""
            Target:
              is: StringHolder
              value: ok
            Host:
              is: StringHolder
              value: ok
              extra: Target
        """.trimIndent())

        val metadata = JvmGraphTestUtils.graphMetadata(graphNotation)

        val extraMetadata = metadata.objectMetadata[location("Host")]!!.attributes[extraAttribute]!!
        assertEquals(
            "Target",
            extraMetadata.attributeMetadataNotation
                .get(NotationConventions.isAttributeSegment)?.asString())
    }


    @Test
    fun `undeclared scalar naming an abstract object stays raw data and host still defines`() {
        val graphNotation = notation("""
            AbstractTarget:
              abstract: true
            Host:
              is: StringHolder
              value: ok
              extra: AbstractTarget
        """.trimIndent())

        val metadata = JvmGraphTestUtils.graphMetadata(graphNotation)
        assertNull(metadata.objectMetadata[location("Host")]!!.attributes[extraAttribute])

        // Before the abstract-target guard, the inferred hard reference got Host pruned here
        val attempt = JvmGraphTestUtils.graphDefinition(graphNotation)
        assertNull(attempt.transitiveFailures[location("Host")])
        assertTrue(location("Host") in attempt.transitiveSuccessful.objectDefinitions)
    }
}
