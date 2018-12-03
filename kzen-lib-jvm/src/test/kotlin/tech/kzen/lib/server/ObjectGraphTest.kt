package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator
import tech.kzen.lib.server.objects.NameAware
import tech.kzen.lib.server.objects.StringHolder


class ObjectGraphTest {
    @Test
    fun `ObjectGraph can be empty`() {
        val emptyMetadata = GraphMetadata(mapOf())

        val emptyDefinition = ObjectGraphDefiner.define(
                ProjectNotation(mapOf()),
                emptyMetadata)

        val emptyGraph = ObjectGraphCreator.createGraph(
                emptyDefinition, emptyMetadata)

        assertEquals(
                ObjectGraphDefiner.bootstrapObjects.size,
                emptyGraph.names().size)
    }


    @Test
    fun `Name-aware object should know its name`() {
        val objectGraph = testObjectGraph()

        val fooNamedInstance = objectGraph.get("FooNamed") as NameAware
        assertEquals("FooNamed", fooNamedInstance.name)
    }


    @Test
    fun `StringHolder can be instantiated`() {
        val objectGraph = testObjectGraph()

        val helloWorldInstance = objectGraph.get("HelloWorldHolder") as StringHolder
        assertEquals("Hello, world!", helloWorldInstance.value)

        val fooNamedInstance = objectGraph.get("FooNamed") as NameAware
        assertEquals("FooNamed", fooNamedInstance.name)
    }


    @Test
    fun `Numeric message can be used in StringHolder`() {
        val objectGraph = testObjectGraph()

        val helloWorldInstance = objectGraph.get("NumericStringHolder") as StringHolder
        assertEquals("123", helloWorldInstance.value)
    }



    private fun testObjectGraph(): ObjectGraph {
        val locator = GradleLocator(true)
        val notationMedia = MultiNotationMedia(listOf(
                FileNotationMedia(locator),
                ClasspathNotationMedia()))

        val notationParser: NotationParser = YamlNotationParser()

        val notationProject = runBlocking {
            val notationProjectBuilder = mutableMapOf<ProjectPath, PackageNotation>()
            for (notationPath in notationMedia.scan()) {
                val notationModule = notationMedia.read(notationPath.key)
                notationProjectBuilder[notationPath.key] = notationParser.parsePackage(notationModule)
            }
            ProjectNotation(notationProjectBuilder)
        }

        val notationMetadataReader = NotationMetadataReader()
        val graphMetadata = notationMetadataReader.read(notationProject)

        val graphDefinition = ObjectGraphDefiner.define(
                notationProject, graphMetadata)

        return ObjectGraphCreator
                .createGraph(graphDefinition, graphMetadata)
    }
}
