package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.context.definition.AttributeDefinition
import tech.kzen.lib.common.context.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


@Suppress("unused")
class ObjectLocationAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(
                        objectLocation))
    }
}