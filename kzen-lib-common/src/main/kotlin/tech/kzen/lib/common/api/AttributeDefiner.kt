package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


interface AttributeDefiner {
    fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt
}