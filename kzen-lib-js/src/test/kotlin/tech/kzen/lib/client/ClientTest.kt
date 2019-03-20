@file:Suppress("unused")

package tech.kzen.lib.client

import tech.kzen.lib.common.api.model.DocumentTree
import tech.kzen.lib.common.api.model.ObjectMap
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import kotlin.test.Test
import kotlin.test.assertEquals


class ClientTest {
    @Test
    fun objectGraphCanBeEmpty() {
        val emptyMetadata = GraphMetadata(ObjectMap(mapOf()))

        val emptyStructure = GraphStructure(
                GraphNotation(DocumentTree(mapOf())),
                emptyMetadata)

        val emptyDefinition = GraphDefiner.define(emptyStructure)

        val emptyGraph = GraphCreator.createGraph(
                emptyStructure, emptyDefinition)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.objects.values.size)
    }
}
