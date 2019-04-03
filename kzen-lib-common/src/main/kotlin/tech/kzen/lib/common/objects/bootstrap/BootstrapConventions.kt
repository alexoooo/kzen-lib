package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.NotationConventions


object BootstrapConventions {
    val rootObjectName = ObjectName("Object")

    val rootObjectLocation = ObjectLocation(
            NotationConventions.kzenBasePath,
            ObjectPath(rootObjectName, ObjectNesting.root))

    val rootObjectReference = rootObjectLocation.toReference()


    val bootstrapObjectName = ObjectName("Bootstrap")

    val bootstrapObjectLocation = ObjectLocation(
            NotationConventions.kzenBasePath,
            ObjectPath(bootstrapObjectName, ObjectNesting.root))

    val bootstrapObjectReference = bootstrapObjectLocation.toReference()
}