package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.edit.RenameObjectCommand
import tech.kzen.lib.common.structure.notation.edit.ShiftObjectCommand
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.structure.notation.repo.NotationRepository
import tech.kzen.lib.platform.IoUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RepositoryTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val metadataReader = NotationMetadataReader()
    private val mainPath = DocumentPath.parse("main.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Move down and back up`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media, yamlParser, metadataReader)

        runBlocking {
            media.writeDocument(mainPath, IoUtils.utf8Encode("""
A:
  hello: "a"
B:
  hello: "b"
"""))

            val aLocation = location("A")

            repo.apply(ShiftObjectCommand(aLocation, PositionIndex(1)))

            assertEquals(1, repo.notation().documents.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "First move down")

            repo.apply(ShiftObjectCommand(aLocation, PositionIndex(0)))

            assertEquals(0, repo.notation().documents.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "Second move back up")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename with space`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media, yamlParser, metadataReader)

        runBlocking {
            media.writeDocument(mainPath, IoUtils.utf8Encode("""
A:
  hello: "a"
"""))

            val aLocation = location("A")

            repo.apply(RenameObjectCommand(aLocation, ObjectName("Foo Bar")))

            val modified = IoUtils.utf8Decode(media.readDocument(mainPath))

            assertTrue(modified.startsWith("\"Foo Bar\":"),
                    "Encoded key expected: $modified")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename with slash`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media, yamlParser, metadataReader)

        runBlocking {
            media.writeDocument(mainPath, IoUtils.utf8Encode("""
A:
  hello: "a"
"""))

            val aLocation = location("A")
            val newName = ObjectName("/")

            repo.apply(RenameObjectCommand(aLocation, newName))

            val modified = IoUtils.utf8Decode(media.readDocument(mainPath))

            assertEquals("""
                "\\/":
                  hello: a
            """.trimIndent(), modified)

            assertEquals(
                    ObjectLocation(
                            mainPath,
                            ObjectPath(newName, ObjectNesting.root)
                    ),
                    repo.aggregate().state.coalesce.locate(ObjectReference(newName, null, null)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributePath {
        return AttributePath.ofName(AttributeName(attribute))
    }
}