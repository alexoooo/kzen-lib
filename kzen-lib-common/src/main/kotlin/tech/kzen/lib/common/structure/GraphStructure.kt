package tech.kzen.lib.common.structure

import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.notation.model.GraphNotation


data class GraphStructure(
        val graphNotation: GraphNotation,
        val graphMetadata: GraphMetadata
)