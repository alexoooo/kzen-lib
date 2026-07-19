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


    /**
     * Registers a service lazily: [provider] runs at most once, on the first
     * [GraphEnvironment.resolve] of [serviceClassName], and the result is memoized. For hosts whose
     * environment must register services that are only constructed after the environment itself
     * (composition-root cycles) - the provider must not be resolved until host construction has
     * completed, which holds as long as resolution happens at request/run time, inside the create
     * chain.
     *
     * Note that trailing-lambda syntax always binds this overload, so a service value that is itself
     * of function type must be registered with the eager overload and an explicit argument.
     */
    fun put(serviceClassName: ClassName, provider: () -> Any): GraphEnvironmentBuilder {
        check(serviceClassName !in services) {
            "Service already registered: $serviceClassName"
        }
        services[serviceClassName] = MapGraphEnvironment.ServiceProvider(provider)
        return this
    }


    fun build(): GraphEnvironment {
        return MapGraphEnvironment(services.toMap())
    }
}
