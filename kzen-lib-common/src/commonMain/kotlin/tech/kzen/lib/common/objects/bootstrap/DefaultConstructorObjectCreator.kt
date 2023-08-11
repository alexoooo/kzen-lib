package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.reflect.Reflect


@Reflect
object DefaultConstructorObjectCreator: ObjectCreator {
    override fun create(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            objectDefinition: ObjectDefinition,
            partialGraphInstance: GraphInstance
    ): ObjectInstance {
        val instance = GlobalMirror
                .create(objectDefinition.className, emptyList())

        return ObjectInstance(
                instance,
                AttributeNameMap.of())
    }
}