package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.ParameterCreator
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.ListParameterDefinition
import tech.kzen.lib.common.definition.ParameterDefinition
import tech.kzen.lib.common.definition.ReferenceParameterDefinition
import tech.kzen.lib.common.definition.ValueParameterDefinition
import tech.kzen.lib.common.metadata.model.ParameterMetadata


class StructuralParameterCreator : ParameterCreator {
    override fun create(
            parameterDefinition: ParameterDefinition,
            parameterMetadata: ParameterMetadata,
            objectGraph: ObjectGraph
    ): Any? {
        return createDefinition(parameterDefinition, objectGraph)
    }


    private fun createDefinition(
            parameterDefinition: ParameterDefinition,
            objectGraph: ObjectGraph
    ): Any? {
        return when (parameterDefinition) {
            is ValueParameterDefinition ->
                parameterDefinition.value

            is ReferenceParameterDefinition ->
                objectGraph.get(parameterDefinition.objectName!!)

            is ListParameterDefinition ->
                parameterDefinition.values.map { createDefinition(it, objectGraph) }

            else ->
                TODO("Not supported (yet): $parameterDefinition")
        }
    }
}