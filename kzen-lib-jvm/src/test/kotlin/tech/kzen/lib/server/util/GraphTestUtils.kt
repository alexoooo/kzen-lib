package tech.kzen.lib.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object GraphTestUtils {
    fun readNotation(): GraphNotation {
        val locator = GradleLocator(true)
        val notationMedia = MultiNotationMedia(listOf(
                FileNotationMedia(locator),
                ClasspathNotationMedia()))

        val notationParser: NotationParser = YamlNotationParser()

        return runBlocking {
            val notationProjectBuilder = mutableMapOf<BundlePath, BundleNotation>()
            for (notationPath in notationMedia.scan().values) {
                val notationModule = notationMedia.read(notationPath.key)
                notationProjectBuilder[notationPath.key] = notationParser.parseBundle(notationModule)
            }
            GraphNotation(BundleTree(notationProjectBuilder))
        }
    }


    fun grapMetadata(notationTree: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(notationTree)
    }


    fun grapDefinition(notationTree: GraphNotation): GraphDefinition {
        val graphMetadata = grapMetadata(notationTree)
        return GraphDefiner.define(
                notationTree, graphMetadata)
    }


    fun newObjectGraph(notationTree: GraphNotation): GraphInstance {
        val graphMetadata = grapMetadata(notationTree)
        val graphDefinition = GraphDefiner.define(
                notationTree, graphMetadata)

        return GraphCreator
                .createGraph(graphDefinition, graphMetadata)
    }


    fun newObjectGraph(): GraphInstance {
        val notationTree = readNotation()
        return newObjectGraph(notationTree)
    }
}