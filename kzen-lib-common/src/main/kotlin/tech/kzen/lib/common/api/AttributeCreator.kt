package tech.kzen.lib.common.api

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


interface AttributeCreator {
    fun create(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any?
}