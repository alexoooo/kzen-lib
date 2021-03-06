package tech.kzen.lib.common.model.structure

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation


data class GraphStructure(
        val graphNotation: GraphNotation,
        val graphMetadata: GraphMetadata
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphStructure(
                GraphNotation.empty,
                GraphMetadata.empty)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filter(allowed: Set<DocumentNesting>): GraphStructure {
        return GraphStructure(
                graphNotation.filter(allowed),
                graphMetadata.filter(allowed))
    }
}