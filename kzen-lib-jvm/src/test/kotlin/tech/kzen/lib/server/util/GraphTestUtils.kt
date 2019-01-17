package tech.kzen.lib.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object GraphTestUtils {
    fun readNotation(): NotationTree {
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
            NotationTree(BundleTree(notationProjectBuilder))
        }
    }


    fun grapMetadata(notationTree: NotationTree): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(notationTree)
    }


    fun grapDefinition(notationTree: NotationTree): GraphDefinition {
        val graphMetadata = grapMetadata(notationTree)
        return ObjectGraphDefiner.define(
                notationTree, graphMetadata)
    }


    fun newObjectGraph(notationTree: NotationTree): ObjectGraph {
        val graphMetadata = grapMetadata(notationTree)
        val graphDefinition = ObjectGraphDefiner.define(
                notationTree, graphMetadata)

        return ObjectGraphCreator
                .createGraph(graphDefinition, graphMetadata)
    }


    fun newObjectGraph(): ObjectGraph {
        val notationTree = readNotation()
        return newObjectGraph(notationTree)
    }
}