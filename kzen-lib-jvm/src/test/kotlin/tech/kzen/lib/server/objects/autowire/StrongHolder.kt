package tech.kzen.lib.server.objects.autowire

import tech.kzen.lib.common.reflect.Reflect


@Reflect
class StrongHolder(
        val concreteObjects: List<ConcreteObject>
)