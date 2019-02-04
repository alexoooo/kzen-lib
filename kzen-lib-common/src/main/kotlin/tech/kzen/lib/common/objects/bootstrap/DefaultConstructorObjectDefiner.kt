package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation


object DefaultConstructorObjectDefiner: ObjectDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            graphNotation: GraphNotation,
            graphMetadata: GraphMetadata,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
        val className = (
                graphNotation.transitiveAttribute(
                        objectLocation, NotationConventions.classAttribute
                )!! as ScalarAttributeNotation
        ).value as String

        val definition = ObjectDefinition(
                className,
                emptyMap(),
                ObjectReference.parse(DefaultConstructorObjectCreator::class.simpleName!!),
                setOf())

        return ObjectDefinitionAttempt.success(
                definition)
    }
}