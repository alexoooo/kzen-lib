package tech.kzen.lib.common.service.context.environment

import tech.kzen.lib.platform.ClassName


class GraphEnvironmentBuilder {
    private val services = mutableMapOf<ClassName, Any?>()


    fun put(serviceClassName: ClassName, service: Any): GraphEnvironmentBuilder {
        check(serviceClassName !in services) {
            "Service already registered: $serviceClassName"
        }
        services[serviceClassName] = service
        return this
    }


    fun build(): GraphEnvironment {
        return MapGraphEnvironment(services.toMap())
    }
}
