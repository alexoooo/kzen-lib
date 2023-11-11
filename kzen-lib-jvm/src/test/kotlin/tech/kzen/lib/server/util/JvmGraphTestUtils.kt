package tech.kzen.lib.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.codegen.KzenLibJvmTestModule
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object JvmGraphTestUtils {
    init {
        KzenLibCommonModule.register()
        KzenLibJvmTestModule.register()
    }


    fun readNotation(): GraphNotation {
        val locator = GradleLocator(true)
        val notationMedia = FileNotationMedia(locator)

        val notationParser: NotationParser = YamlNotationParser()

        return runBlocking {
            val notationProjectBuilder =
                mutableMapOf<DocumentPath, DocumentNotation>()

            for (notationPath in notationMedia.scan().documents.values) {
                val notationModule = notationMedia.readDocument(notationPath.key)
                val objects = notationParser.parseDocumentObjects(notationModule)
                notationProjectBuilder[notationPath.key] = DocumentNotation(
                        objects,
                        null)
            }
            GraphNotation(DocumentPathMap(
                    notationProjectBuilder.toPersistentMap()))
        }
    }


    fun graphMetadata(graphNotation: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(graphNotation)
    }


    fun graphDefinition(graphNotation: GraphNotation): GraphDefinitionAttempt {
        val graphMetadata = graphMetadata(graphNotation)
        return GraphDefiner().tryDefine(
                GraphStructure(graphNotation, graphMetadata))
    }


    fun newObjectGraph(graphNotation: GraphNotation): GraphInstance {
        val graphMetadata = graphMetadata(graphNotation)
        val graphStructure = GraphStructure(graphNotation, graphMetadata)

        val definitionAttempt = GraphDefiner().tryDefine(graphStructure)
//        if (definitionAttempt.objectDefinitions.contains(ObjectLocation.parse("test/kzen-test.yaml#StringHolderNullableNominal"))) {
//            println("foo")
//        }

        val graphDefinition = definitionAttempt.transitiveSuccessful()
//        if (graphDefinition.objectDefinitions.contains(ObjectLocation.parse("test/kzen-test.yaml#StringHolderNullableNominal"))) {
//            println("foo")
//        }

        return GraphCreator()
                .createGraph(graphDefinition)
    }


    fun newObjectGraph(): GraphInstance {
        val graphNotation = readNotation()
        return newObjectGraph(graphNotation)
    }
}