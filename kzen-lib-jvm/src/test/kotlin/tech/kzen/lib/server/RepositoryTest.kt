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
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectCommand
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
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

        val repo = DirectGraphStore(
                media, yamlParser, metadataReader, GraphDefiner(), NotationReducer())

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
B:
  hello: "b"
""")

            val aLocation = location("A")

            repo.apply(ShiftObjectCommand(aLocation, PositionRelation.at(1)))

            assertEquals(
                    1,
                    repo.graphNotation().documents.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "First move down")

            repo.apply(ShiftObjectCommand(aLocation, PositionRelation.first))

            assertEquals(
                    0,
                    repo.graphNotation().documents.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "Second move back up")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename with space`() {
        val media = MapNotationMedia()

        val repo = DirectGraphStore(
                media, yamlParser, metadataReader, GraphDefiner(), NotationReducer())

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")

            val aLocation = location("A")

            repo.apply(RenameObjectCommand(aLocation, ObjectName("Foo Bar")))

            val modified = media.readDocument(mainPath)

            assertTrue(modified.startsWith("Foo Bar:"),
                    "Encoded key expected: $modified")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename with slash`() {
        val media = MapNotationMedia()

        val repo = DirectGraphStore(
                media, yamlParser, metadataReader, GraphDefiner(), NotationReducer())

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")

            val aLocation = location("A")
            val newName = ObjectName("/")

            repo.apply(RenameObjectCommand(aLocation, newName))

            val modified = media.readDocument(mainPath)

            assertEquals("""
                "\\/":
                  hello: a
            """.trimIndent(), modified)

            assertEquals(
                    ObjectLocation(
                            mainPath,
                            ObjectPath(newName, ObjectNesting.root)
                    ),
                    repo.graphNotation().coalesce.locate(ObjectReference.ofRootName(newName)))
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