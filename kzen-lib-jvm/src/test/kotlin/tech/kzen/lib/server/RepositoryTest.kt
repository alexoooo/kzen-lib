package tech.kzen.lib.server

import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import tech.kzen.lib.common.edit.ShiftObjectCommand
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.IoUtils
import kotlin.test.assertEquals


class RepositoryTest {

    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val mainPath = ProjectPath("main.yaml")


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

            repo.apply(ShiftObjectCommand("A", 1))

            assertEquals(1, repo.notation().packages[mainPath]!!.indexOf("A"),
                    "First move down")

            repo.apply(ShiftObjectCommand("A", 0))

            assertEquals(0, repo.notation().packages[mainPath]!!.indexOf("A"),
                    "Second move back up")
        }
    }
}