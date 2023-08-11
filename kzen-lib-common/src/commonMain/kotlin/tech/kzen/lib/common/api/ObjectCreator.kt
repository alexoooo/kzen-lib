package tech.kzen.lib.common.api

import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


interface ObjectCreator {
    fun create(
        objectLocation: ObjectLocation,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance
    ): ObjectInstance
}