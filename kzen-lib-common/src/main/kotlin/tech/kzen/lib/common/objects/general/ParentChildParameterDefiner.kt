package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.ParameterDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ListParameterDefinition
import tech.kzen.lib.common.definition.ParameterDefinition
import tech.kzen.lib.common.definition.ReferenceParameterDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


@Suppress("unused")
class ParentChildParameterDefiner : ParameterDefiner {
    companion object {
        private const val parentParameterName = "parent"
    }

    override fun define(
            objectName: String,
            parameterName: String,
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ParameterDefinition {
        val children = mutableListOf<String>()

        for (e in projectNotation.coalesce) {
            val parentNotation = projectNotation
                    .transitiveParameter(e.key, parentParameterName)
                    ?: continue

            val parentName =
                    (parentNotation as? ScalarParameterNotation)?.value
                    ?: continue

            if (parentName != objectName) {
                continue
            }

            children.add(e.key)
        }

        return ListParameterDefinition(
                children.map { ReferenceParameterDefinition(it) })
    }
}