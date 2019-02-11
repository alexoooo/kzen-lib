package tech.kzen.lib.common.api

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata


interface ObjectCreator {
    fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            graphInstance: GraphInstance
    ): Any
}