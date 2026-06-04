package tech.kzen.lib.common.service.context.environment

import tech.kzen.lib.platform.ClassName


/**
 * Host-supplied registry of runtime services, keyed by the service's declared type. Threaded through
 * [tech.kzen.lib.common.service.context.GraphCreator.createGraph] into the object-creation chain so
 * that constructor parameters annotated [tech.kzen.lib.common.reflect.Service] can be filled at
 * instantiation time with values that can't be expressed in notation (compilers, stores, web-driver
 * holders, …).
 *
 * Definition stays environment-free (definitions are pure and cached); only the create chain carries
 * the environment. Resolution is by the parameter's declared [ClassName], so each service is
 * registered under the type its consumers declare (which may be an interface).
 */
interface GraphEnvironment {
    fun resolve(serviceClassName: ClassName): Any?

    fun contains(serviceClassName: ClassName): Boolean

    val serviceClassNames: Set<ClassName>


    companion object {
        val className = ClassName("tech.kzen.lib.common.service.context.environment.GraphEnvironment")

        val empty: GraphEnvironment = MapGraphEnvironment(mapOf())

        fun builder(): GraphEnvironmentBuilder {
            return GraphEnvironmentBuilder()
        }
    }
}
