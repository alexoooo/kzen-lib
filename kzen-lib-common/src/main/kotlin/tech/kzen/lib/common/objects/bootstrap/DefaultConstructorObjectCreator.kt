package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.platform.Mirror


object DefaultConstructorObjectCreator: ObjectCreator {
    override fun create(
            objectLocation: ObjectLocation,
            objectDefinition: ObjectDefinition,
            objectMetadata: ObjectMetadata,
            objectGraph: GraphInstance
    ): Any {
        check(objectDefinition.constructorArguments.isEmpty())
        return Mirror
                .create(objectDefinition.className, emptyList())
    }
}