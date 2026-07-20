package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.platformSynchronized


object GlobalMirror: ClassMirror {
    private val delegates = mutableListOf<ClassMirror>(
        ReflectionRegistry.global
    )


    /**
     * Append a fallback delegate to the mirror chain. Delegates are consulted in registration order;
     * [ReflectionRegistry.global] is seeded first and always wins, so generated (KSP) registrations
     * shadow any fallback and a fallback only sees genuine misses. Registering the same instance
     * again is a no-op.
     *
     * JS never registers anything here (no runtime reflection exists there): the chain stays
     * single-delegate and behaviour is unchanged.
     */
    fun register(delegate: ClassMirror) {
        platformSynchronized(delegates) {
            if (delegates.none { it === delegate }) {
                delegates.add(delegate)
            }
        }
    }


    // Snapshot then iterate outside the monitor: delegate calls may run user constructor code.
    private fun delegateSnapshot(): List<ClassMirror> {
        return platformSynchronized(delegates) {
            delegates.toList()
        }
    }


    override fun contains(className: ClassName): Boolean {
        for (delegate in delegateSnapshot()) {
            if (delegate.contains(className)) {
                return true
            }
        }
        return false
    }


    override fun constructorArgumentNames(className: ClassName): List<String> {
        for (delegate in delegateSnapshot()) {
            if (delegate.contains(className)) {
                return delegate.constructorArgumentNames(className)
            }
        }
        throw IllegalArgumentException("Unknown: $className")
    }


    override fun serviceArguments(className: ClassName): Map<String, ClassName> {
        for (delegate in delegateSnapshot()) {
            if (delegate.contains(className)) {
                return delegate.serviceArguments(className)
            }
        }
        return mapOf()
    }


    override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        for (delegate in delegateSnapshot()) {
            if (delegate.contains(className)) {
                return delegate.create(className, constructorArguments)
            }
        }
        throw IllegalArgumentException("Unknown: $className")
    }
}
