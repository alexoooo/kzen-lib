package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ParameterDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation


interface ParameterDefiner {
    fun define(
            objectName: String,
            parameterName: String,
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ParameterDefinition
}