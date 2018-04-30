package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


object DefaultConstructorObjectDefiner : ObjectDefiner {
    override fun define(
            objectName: String,
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): ObjectDefinitionAttempt {
        val className = (
                projectNotation.transitiveParameter(
                        objectName, "class"
                )!! as ScalarParameterNotation
        ).value as String

        val definition = ObjectDefinition(
                className,
                emptyMap(),
                DefaultConstructorObjectCreator::class.simpleName!!,
                setOf())

        return ObjectDefinitionAttempt.success(
                definition)
    }
}