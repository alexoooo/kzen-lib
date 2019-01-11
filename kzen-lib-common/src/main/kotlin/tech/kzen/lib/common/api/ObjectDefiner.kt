package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.api.model.ObjectLocation


interface ObjectDefiner {
    fun define(
            objectLocation: ObjectLocation,
            notationTree: NotationTree,
            graphMetadata: GraphMetadata,
            graphDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ObjectDefinitionAttempt
}