package tech.kzen.lib.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.DocumentTree
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
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
            val notationProjectBuilder = mutableMapOf<DocumentPath, DocumentNotation>()
            for (notationPath in notationMedia.scan().values) {
                val notationModule = notationMedia.read(notationPath.key)
                notationProjectBuilder[notationPath.key] = notationParser.parseDocument(notationModule)
            }
            GraphNotation(DocumentTree(notationProjectBuilder))
        }
    }


    fun grapMetadata(notationTree: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(notationTree)
    }


    fun grapDefinition(notationTree: GraphNotation): GraphDefinition {
        val graphMetadata = grapMetadata(notationTree)
        return GraphDefiner.define(
                GraphStructure(notationTree, graphMetadata))
    }


    fun newObjectGraph(notationTree: GraphNotation): GraphInstance {
        val graphMetadata = grapMetadata(notationTree)
        val graphStructure = GraphStructure(notationTree, graphMetadata)

        val graphDefinition = GraphDefiner.define(graphStructure)

        return GraphCreator
                .createGraph(graphStructure, graphDefinition)
    }


    fun newObjectGraph(): GraphInstance {
        val notationTree = readNotation()
        return newObjectGraph(notationTree)
    }
}