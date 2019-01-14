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
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator
import tech.kzen.lib.server.objects.ast.DoubleExpression


class AstGraphTest {
    @Test
    fun `literal 2 + 2 = 4`() {
        val objectGraph = astObjectGraph()

        val twoPlusTwoLocation = ObjectLocation(
                BundlePath.parse("test/nested-test.yaml"),
                ObjectPath.parse("TwoPlusTwo"))

        val fooNamedInstance = objectGraph.objects.get(twoPlusTwoLocation) as DoubleExpression
        assertEquals(4.0, fooNamedInstance.evaluate(), 0.0)
    }


    @Test
    fun `inline 2 + 2 = 4`() {
        val objectGraph = astObjectGraph()

        val twoPlusTwoLocation = ObjectLocation(
                BundlePath.parse("test/nested-test.yaml"),
                ObjectPath.parse("TwoPlusTwoInlineMap"))

        val fooNamedInstance = objectGraph.objects.get(twoPlusTwoLocation) as DoubleExpression
        assertEquals(4.0, fooNamedInstance.evaluate(), 0.0)
    }


    private fun astObjectGraph(): ObjectGraph {
        val locator = GradleLocator(true)
        val notationMedia = MultiNotationMedia(listOf(
                FileNotationMedia(locator),
                ClasspathNotationMedia()))

        val notationParser: NotationParser = YamlNotationParser()

        val notationProject = runBlocking {
            val notationProjectBuilder = mutableMapOf<BundlePath, BundleNotation>()
            for (notationPath in notationMedia.scan().values) {
                val notationModule = notationMedia.read(notationPath.key)
                notationProjectBuilder[notationPath.key] = notationParser.parseBundle(notationModule)
            }
            NotationTree(BundleTree(notationProjectBuilder))
        }

        val notationMetadataReader = NotationMetadataReader()
        val graphMetadata = notationMetadataReader.read(notationProject)

        val graphDefinition = ObjectGraphDefiner.define(
                notationProject, graphMetadata)

        return ObjectGraphCreator
                .createGraph(graphDefinition, graphMetadata)
    }
}
