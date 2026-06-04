package tech.kzen.lib.common.service.context.environment

import tech.kzen.lib.platform.ClassName


class MapGraphEnvironment(
    private val services: Map<ClassName, Any?>
): GraphEnvironment {
    override fun resolve(serviceClassName: ClassName): Any? {
        // A GraphEnvironment-typed @Service parameter resolves to the environment itself, so a
        // graph object (e.g. a document that hand-builds a downstream execution) can re-enter the
        // create chain with the same services.
        if (serviceClassName == GraphEnvironment.className) {
            return this
        }

        if (serviceClassName !in services) {
            throw IllegalArgumentException("Missing service: $serviceClassName - have $serviceClassNames")
        }
        return services[serviceClassName]
    }


    override fun contains(serviceClassName: ClassName): Boolean {
        return serviceClassName == GraphEnvironment.className ||
                serviceClassName in services
    }


    override val serviceClassNames: Set<ClassName>
        get() = services.keys
}
