package tech.kzen.lib.common.service.store

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


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


    @Test
    fun `Concurrent applies and definition reads stay coherent`() {
        val media = MapNotationMedia()
        val store = newStore(media)

        runBlocking {
            media.writeDocument(mainPath, """
A:
  hello: "a"
""")
            store.graphDefinition()
        }

        // One writer toggling A<->B through apply(), several readers pulling graphDefinition(): every returned
        // attempt must be internally consistent — exactly one object, named either A or B, never a digest paired
        // with a torn cache value.
        val iterations = 200
        val readerCount = 4
        val failures = ConcurrentLinkedQueue<Throwable>()

        val writer = thread {
            try {
                runBlocking {
                    repeat(iterations) { i ->
                        val from = if (i % 2 == 0) "A" else "B"
                        val to = if (i % 2 == 0) "B" else "A"
                        store.apply(RenameObjectCommand(
                            ObjectLocation(mainPath, ObjectPath.parse(from)),
                            ObjectName(to)))
                    }
                }
            }
            catch (t: Throwable) {
                failures.add(t)
            }
        }

        val readers = (0 until readerCount).map {
            thread {
                try {
                    runBlocking {
                        repeat(iterations) {
                            assertSingleToggledObject(store.graphDefinition())
                        }
                    }
                }
                catch (t: Throwable) {
                    failures.add(t)
                }
            }
        }

        writer.join()
        readers.forEach { it.join() }

        assertTrue(failures.isEmpty(), failures.joinToString { it.message ?: it.toString() })
    }


    private fun assertSingleToggledObject(graphDefinitionAttempt: GraphDefinitionAttempt) {
        val objectNames = graphDefinitionAttempt
            .graphStructure
            .graphNotation
            .documents[mainPath]!!
            .objects
            .notations
            .map
            .keys
            .map { it.name.value }

        check(objectNames.size == 1 && objectNames.single() in setOf("A", "B")) {
            "Torn read: $objectNames"
        }
    }
}
