package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.api.model.ObjectLocation


interface ObjectCreator {
    fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            objectGraph: GraphInstance
    ): Any
}