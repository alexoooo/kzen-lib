package tech.kzen.lib.common.api

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation


interface ObjectDefiner {
    fun define(
            objectLocation: ObjectLocation,
            graphNotation: GraphNotation,
            graphMetadata: GraphMetadata,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance
    ): ObjectDefinitionAttempt
}