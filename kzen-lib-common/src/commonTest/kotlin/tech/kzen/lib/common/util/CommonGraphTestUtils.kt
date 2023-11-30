package tech.kzen.lib.common.util

import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.metadata.NotationMetadataReader


object CommonGraphTestUtils {
    init {
        KzenLibCommonModule.register()
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
        val graphDefinition = definitionAttempt.transitiveSuccessful

        return GraphCreator()
                .createGraph(graphDefinition)
    }
}