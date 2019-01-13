package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.edit.RenameObjectCommand
import tech.kzen.lib.common.notation.edit.ShiftObjectCommand
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.model.PositionIndex
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.platform.IoUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RepositoryTest {

    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val mainPath = BundlePath.parse("main.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Move down and back up`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media, yamlParser)

        runBlocking {
            media.write(mainPath, IoUtils.stringToUtf8("""
A:
  hello: "a"
B:
  hello: "b"
"""))

            val aLocation = location("A")

            repo.apply(ShiftObjectCommand(aLocation, PositionIndex(1)))

            assertEquals(1, repo.notation().bundleNotations.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "First move down")

            repo.apply(ShiftObjectCommand(aLocation, PositionIndex(0)))

            assertEquals(0, repo.notation().bundleNotations.values[mainPath]!!.indexOf(aLocation.objectPath).value,
                    "Second move back up")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename with space`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media, yamlParser)

        runBlocking {
            media.write(mainPath, IoUtils.stringToUtf8("""
A:
  hello: "a"
"""))

            val aLocation = location("A")

            repo.apply(RenameObjectCommand(aLocation, ObjectName("Foo Bar")))

            val modified = IoUtils.utf8ToString(media.read(mainPath))

            assertTrue(modified.startsWith("\"Foo Bar\":"),
                    "Encoded key expected: $modified")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(mainPath, ObjectPath.parse(name))
    }

    private fun attribute(attribute: String): AttributeNesting {
        return AttributeNesting.ofAttribute(AttributeName(attribute))
    }
}