package tech.kzen.lib.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.platform.collect.toPersistentMap
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
            GraphNotation(DocumentPathMap(
                    notationProjectBuilder.toPersistentMap()))
        }
    }


    fun grapMetadata(graphNotation: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(graphNotation)
    }


    fun grapDefinition(graphNotation: GraphNotation): GraphDefinition {
        val graphMetadata = grapMetadata(graphNotation)
        return GraphDefiner.define(
                GraphStructure(graphNotation, graphMetadata))
    }


    fun newObjectGraph(graphNotation: GraphNotation): GraphInstance {
        val graphMetadata = grapMetadata(graphNotation)
        val graphStructure = GraphStructure(graphNotation, graphMetadata)

        val graphDefinition = GraphDefiner.define(graphStructure)

        return GraphCreator
                .createGraph(graphStructure, graphDefinition)
    }


    fun newObjectGraph(): GraphInstance {
        val graphNotation = readNotation()
        return newObjectGraph(graphNotation)
    }
}