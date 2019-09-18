@file:Suppress("unused")

package tech.kzen.lib.client

import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import kotlin.test.Test
import kotlin.test.assertEquals


class ClientTest {
    @Test
    fun objectGraphCanBeEmpty() {
        val emptyStructure = GraphStructure(GraphNotation.empty, GraphMetadata.empty)

        val emptyDefinition = GraphDefiner().define(emptyStructure)

        val emptyGraph = GraphCreator().createGraph(
                emptyDefinition)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.size)
    }
}
