@file:Suppress("unused")

package tech.kzen.lib.client

import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.api.model.ObjectMap
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.NotationTree
import kotlin.test.Test
import kotlin.test.assertEquals


class ClientTest {
    @Test
    fun objectGraphCanBeEmpty() {
        val emptyMetadata = GraphMetadata(ObjectMap(mapOf()))

        val emptyDefinition = ObjectGraphDefiner.define(
                NotationTree(BundleTree(mapOf())),
                emptyMetadata)

        val emptyGraph = ObjectGraphCreator.createGraph(
                emptyDefinition, emptyMetadata)

        assertEquals(
                ObjectGraphDefiner.bootstrapObjects.size,
                emptyGraph.objects.values.size)
    }
}
