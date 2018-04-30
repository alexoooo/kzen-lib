package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation


class ServerTest {
    @Test
    fun `ObjectGraph can be empty`() {
        val emptyMetadata = GraphMetadata(mapOf())

        val emptyDefinition = ObjectGraphDefiner.define(
                ProjectNotation(mapOf()),
                emptyMetadata)

        val emptyGraph = ObjectGraphCreator.createGraph(
                emptyDefinition, emptyMetadata)

        assertEquals(
                ObjectGraphDefiner.bootstrapObjects.size,
                emptyGraph.names().size)
    }
}
