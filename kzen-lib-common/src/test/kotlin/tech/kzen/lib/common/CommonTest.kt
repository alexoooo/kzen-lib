package tech.kzen.lib.common

import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonTest {
    @Test
    fun objectGraphCanBeEmpty() {
        val emptyMetadata = GraphMetadata(ObjectLocationMap(persistentMapOf()))

        val graphStructure = GraphStructure(
                GraphNotation(DocumentPathMap(persistentMapOf())),
                emptyMetadata)

        val emptyDefinition = GraphDefiner.define(graphStructure)

        val emptyGraph = GraphCreator.createGraph(
                graphStructure, emptyDefinition)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.size)
    }
}
