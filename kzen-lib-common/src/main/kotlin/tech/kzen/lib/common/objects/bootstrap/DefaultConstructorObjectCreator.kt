package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.Mirror


object DefaultConstructorObjectCreator: ObjectCreator {
    override fun create(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): Any {
//        check(objectDefinition.attributeDefinitions.isEmpty())
        return Mirror
                .create(objectDefinition.className, emptyList())
    }
}