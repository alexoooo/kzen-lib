package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata


interface ObjectCreator {
    fun create(
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            objectGraph: ObjectGraph
    ): Any
}