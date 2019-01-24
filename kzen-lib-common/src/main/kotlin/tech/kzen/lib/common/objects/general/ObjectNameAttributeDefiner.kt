package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation


@Suppress("unused")
class ObjectNameAttributeDefiner: AttributeDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            projectNotation: GraphNotation,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: GraphInstance
    ): AttributeDefinition {
        return ValueAttributeDefinition(
                objectLocation.objectPath.name)
    }
}