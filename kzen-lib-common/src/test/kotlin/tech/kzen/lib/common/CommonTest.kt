package tech.kzen.lib.common

import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.api.model.ObjectMap
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonTest {
    @Test
    fun objectGraphCanBeEmpty() {
        val emptyMetadata = GraphMetadata(ObjectMap(mapOf()))

        val emptyDefinition = GraphDefiner.define(
                GraphNotation(BundleTree(mapOf())),
                emptyMetadata)

        val emptyGraph = GraphCreator.createGraph(
                emptyDefinition, emptyMetadata)

        assertEquals(
                GraphDefiner.bootstrapObjects.size,
                emptyGraph.objects.values.size)
    }
}
