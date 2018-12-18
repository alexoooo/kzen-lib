package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ParameterDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.TypeMetadata
import tech.kzen.lib.common.notation.model.ListParameterNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.platform.ClassNames


class NotationParameterDefiner : ParameterDefiner {
    override fun define(
            objectName: String,
            parameterName: String,
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ParameterDefinition {
        val objectNotation = projectNotation.coalesce[objectName]!!

        // TODO: is the transitiveParameter here handled correctly? what about default values?
        val parameterNotation = objectNotation.parameters[parameterName]
                ?: projectNotation.transitiveParameter(objectName, parameterName)
                ?: throw IllegalArgumentException("Unknown parameter: $parameterName")

        val objectMetadata = projectMetadata.objectMetadata[objectName]!!
        val parameterMetadata = objectMetadata.parameters[parameterName]!!

        val typeMetadata = parameterMetadata.type!!

        return defineRecursively(parameterNotation, typeMetadata)
    }


    private fun defineRecursively(
            parameterNotation: ParameterNotation,
            typeMetadata: TypeMetadata
    ): ParameterDefinition {
        if (parameterNotation is ScalarParameterNotation) {
            val className = typeMetadata.className

            if (parameterNotation.value is String && className != ClassNames.kotlinString) {
                return ReferenceParameterDefinition(parameterNotation.value)
            }

            if (className == ClassNames.kotlinString && parameterNotation.value !is String) {
                return ValueParameterDefinition(parameterNotation.value.toString())
            }

            return ValueParameterDefinition(parameterNotation.value)
        }
        else if (parameterNotation is ListParameterNotation) {
            val listGeneric = typeMetadata.generics[0]

            val definitions = mutableListOf<ParameterDefinition>()
            for (value in parameterNotation.values) {
                val definition = defineRecursively(value, listGeneric)
                definitions.add(definition)
            }
            return ListParameterDefinition(definitions)
        }

        TODO()
    }
}