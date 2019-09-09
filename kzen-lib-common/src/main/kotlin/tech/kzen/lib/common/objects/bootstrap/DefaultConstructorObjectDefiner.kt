package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ObjectDefinition
import tech.kzen.lib.common.context.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassName


object DefaultConstructorObjectDefiner: ObjectDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
        val className = ClassName((
                graphStructure.graphNotation.transitiveAttribute(
                        objectLocation, NotationConventions.classAttributePath
                )!! as ScalarAttributeNotation
        ).value)

        val definition = ObjectDefinition(
                className,
                AttributeNameMap.of(),
                ObjectReference.parse(DefaultConstructorObjectCreator::class.simpleName!!),
                setOf())

        return ObjectDefinitionAttempt.success(
                definition)
    }
}