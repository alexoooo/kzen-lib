package tech.kzen.lib.common.objects.bootstrap

import tech.kzen.lib.common.api.model.BundleNesting
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.structure.notation.NotationConventions


object BootstrapConventions {
    val rootObjectName = ObjectName("Object")

    val rootObjectLocation = ObjectLocation(
            NotationConventions.kzenBasePath,
            ObjectPath(rootObjectName, BundleNesting.root))

    val rootObjectReference = rootObjectLocation.toReference()


    val bootstrapObjectName = ObjectName("Bootstrap")

    val bootstrapLocation = ObjectLocation(
            NotationConventions.kzenBasePath,
            ObjectPath(bootstrapObjectName, BundleNesting.root))

    val bootstrapReference = bootstrapLocation.toReference()
}