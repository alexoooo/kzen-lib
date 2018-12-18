package tech.kzen.lib.server


import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
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
import tech.kzen.lib.server.objects.ast.DoubleExpression


class AstGraphTest {
    @Test
    fun `2 + 2 = 4`() {
        val objectGraph = astObjectGraph()

        val fooNamedInstance = objectGraph.get("TwoPlusTwo") as DoubleExpression
        assertEquals(4.0, fooNamedInstance.evaluate(), 0.0)
    }


    private fun astObjectGraph(): ObjectGraph {
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
