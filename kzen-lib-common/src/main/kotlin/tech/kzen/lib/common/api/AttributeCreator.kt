package tech.kzen.lib.common.api

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.metadata.model.AttributeMetadata


interface AttributeCreator {
    fun create(
            objectLocation: ObjectLocation,
            attributeDefinition: AttributeDefinition,
            attributeMetadata: AttributeMetadata,
            graphInstance: GraphInstance
    ): Any?
}