package tech.kzen.lib.common.api

import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation


interface AttributeDefiner {
    fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            projectNotation: GraphNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: GraphInstance
    ): AttributeDefinition
}