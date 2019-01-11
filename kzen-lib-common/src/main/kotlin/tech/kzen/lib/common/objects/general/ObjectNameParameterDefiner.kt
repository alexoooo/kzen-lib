package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.ParameterDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.ValueAttributeDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation


@Suppress("unused")
class ObjectNameParameterDefiner: ParameterDefiner {
    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            projectNotation: NotationTree,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): AttributeDefinition {
        return ValueAttributeDefinition(
                objectLocation.objectPath.name.value)
    }
}