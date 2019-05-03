package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.context.definition.ObjectDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.context.instance.ObjectInstance
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.platform.Mirror


object DefaultConstructorObjectCreator: ObjectCreator {
    override fun create(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): ObjectInstance {
        val instance = Mirror
                .create(objectDefinition.className, emptyList())

        return ObjectInstance(
                instance,
                AttributeNameMap.of())
    }
}