package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ParameterDefinition
import tech.kzen.lib.common.metadata.model.ParameterMetadata


interface ParameterCreator {
    fun create(
            parameterDefinition: ParameterDefinition,
            parameterMetadata: ParameterMetadata,
            objectGraph: ObjectGraph
    ): Any?
}