package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


interface ObjectCreator {
    fun create(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any
}