package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals


/**
 * Renaming an `abstract: true` object rewrites the weak (`by: Nominal`) references that name it.
 *
 * Abstract objects have no ObjectDefinition and no definition failure, so `isReferenced` cannot find them in
 * either map — it must fall back to the notation coalesce. Without the fallback, renaming an abstract
 * archetype named as data (a Context declaration, a branchArchetype) silently left every reference dangling.
 */
class RenameAbstractTargetTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()

    private val documentPath = DocumentPath.parse("test/rename-abstract-target-test.yaml")

    private val hostLocation = ObjectLocation(documentPath, ObjectPath.parse("Host"))
    private val extraPath = AttributePath.parse("extra")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `renaming an abstract object rewrites weak references to it`() {
        val graphNotation = JvmGraphTestUtils
            .readNotation()
            .withNewDocument(
                documentPath,
                DocumentNotation(yamlParser.parseDocumentObjects("""
                    AbstractTarget:
                      abstract: true
                    Host:
                      is: StringHolder
                      value: ok
                      extra: AbstractTarget
                      meta:
                        extra:
                          is: ObjectLocation
                          by: Nominal
                """.trimIndent()), null))

        val attempt = JvmGraphTestUtils.graphDefinition(graphNotation)

        val renamed = NotationReducer()
            .applySemantic(
                attempt,
                RenameObjectRefactorCommand(
                    ObjectLocation(documentPath, ObjectPath.parse("AbstractTarget")),
                    ObjectName("RenamedTarget")))
            .graphNotation

        assertEquals(
            "RenamedTarget",
            renamed.firstAttribute(hostLocation, extraPath)?.asString())
    }
}
