package tech.kzen.lib.server.objects.autowire

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class WeakHolder(
    val locations: List<ObjectLocation>
)