package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.platformSynchronized


/**
 * Registration happens at module-register time (boot) and reads happen afterward, but [global] is a
 * process-wide singleton on the JVM, so access is synchronized (contention is nil).
 */
class ReflectionRegistry: ClassMirror {
    companion object {
        val simpleName = ReflectionRegistry::class.simpleName!!
        val qualifiedName = "tech.kzen.lib.common.reflect.$simpleName"
        val className = ClassName(qualifiedName)

        val global = ReflectionRegistry()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val registry = mutableMapOf<ClassName, ClassReflection>()


    //-----------------------------------------------------------------------------------------------------------------
    fun get(className: ClassName): ClassReflection? {
        return platformSynchronized(registry) {
            registry[className]
        }
    }


    fun put(className: String,
            constructorArgumentNames: List<String>,
            serviceArguments: Map<String, String> = mapOf(),
            constructorFunction: (List<Any?>) -> Any
    ) {
        platformSynchronized(registry) {
            registry[ClassName(className)] = ClassReflection(
                constructorArgumentNames,
                serviceArguments.mapValues { ClassName(it.value) },
                constructorFunction)
        }
    }


    /**
     * Every distinct [Service] parameter type across all registrations, mapped to the registered class(es)
     * declaring it. Supports host boot-time validation that a
     * [tech.kzen.lib.common.service.context.environment.GraphEnvironment] covers every service type a
     * registered class can demand, failing at startup with names instead of at graph-creation time.
     * Snapshot taken under the registry lock.
     */
    fun serviceArgumentDeclarations(): Map<ClassName, Set<ClassName>> {
        return platformSynchronized(registry) {
            val declarations = mutableMapOf<ClassName, MutableSet<ClassName>>()
            for ((className, classReflection) in registry) {
                for (serviceClassName in classReflection.serviceArguments.values) {
                    declarations.getOrPut(serviceClassName) { mutableSetOf() }.add(className)
                }
            }
            declarations
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(className: ClassName): Boolean {
        return get(className) != null
    }


    override fun constructorArgumentNames(className: ClassName): List<String> {
        return get(className)?.constructorArgumentNames
                ?: throw IllegalArgumentException("Not found: $className")
    }


    override fun serviceArguments(className: ClassName): Map<String, ClassName> {
        return get(className)?.serviceArguments ?: mapOf()
    }


    override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        return get(className)?.constructorFunction?.invoke(constructorArguments)
                ?: throw IllegalArgumentException("Not found: $className")
    }
}