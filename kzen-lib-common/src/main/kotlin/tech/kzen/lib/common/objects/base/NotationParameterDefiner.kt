package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ParameterDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ParameterDefinition
import tech.kzen.lib.common.definition.ReferenceParameterDefinition
import tech.kzen.lib.common.definition.ValueParameterDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


class NotationParameterDefiner : ParameterDefiner {
    companion object {
        private const val stringQualifiedName = "kotlin.String"
    }

    override fun define(
            objectName: String,
            parameterName: String,
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ParameterDefinition {
        val objectNotation = projectNotation.coalesce[objectName]!!
        val parameterNotation = objectNotation.parameters[parameterName]!!

        val objectMetadata = projectMetadata.objectMetadata[objectName]!!
        val parameterMetadata = objectMetadata.parameters[parameterName]!!

        val typeMetadata = parameterMetadata.type!!

        // TODO: perform recursive parameterized definition
        if (parameterNotation is ScalarParameterNotation) {
            if (parameterNotation.value is String && typeMetadata.className != stringQualifiedName) {
                return ReferenceParameterDefinition(parameterNotation.value)
            }
            return ValueParameterDefinition(parameterNotation.value)
        }

        TODO()
    }
}