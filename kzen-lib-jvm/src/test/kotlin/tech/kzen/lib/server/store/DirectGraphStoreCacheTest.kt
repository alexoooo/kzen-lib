package tech.kzen.lib.server.store

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectCommand
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import kotlin.test.assertNotSame
import kotlin.test.assertSame


class DirectGraphStoreCacheTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = DocumentPath.parse("main.yaml")


    private fun newStore(media: MapNotationMedia): DirectGraphStore {
        return DirectGraphStore(
            media, YamlNotationParser(), NotationMetadataReader(), GraphDefiner, NotationReducer())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Unchanged notation returns the same definition attempt instance`() {
        val media = MapNotationMedia()
        val store = newStore(media)

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")

            val first = store.graphDefinition()
            val second = store.graphDefinition()
            assertSame(first, second)
        }
    }


    @Test
    fun `Command yields a fresh definition attempt, then cached again`() {
        val media = MapNotationMedia()
        val store = newStore(media)

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")

            val first = store.graphDefinition()

            store.apply(RenameObjectCommand(
                ObjectLocation(mainPath, ObjectPath.parse("A")),
                ObjectName("B")))

            val afterCommand = store.graphDefinition()
            assertNotSame(first, afterCommand)
            assertSame(afterCommand, store.graphDefinition())
        }
    }


    @Test
    fun `Refresh clears the definition cache`() {
        val media = MapNotationMedia()
        val store = newStore(media)

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")

            val first = store.graphDefinition()

            store.refresh()

            assertNotSame(first, store.graphDefinition())
        }
    }
}
