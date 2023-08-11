package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ObjectDefinitionAttempt
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName


/**
 * https://en.wikipedia.org/wiki/Default_constructor
 */
@Reflect
object DefaultConstructorObjectDefiner: ObjectDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): ObjectDefinitionAttempt {
        val className = ClassName((
                graphStructure.graphNotation.firstAttribute(
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