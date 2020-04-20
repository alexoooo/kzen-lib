package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName


object GlobalMirror: ClassMirror {
    private val delegates = listOf(
        ReflectionRegistry.global
    )


    override fun contains(className: ClassName): Boolean {
        for (delegate in delegates) {
            if (delegate.contains(className)) {
                return true
            }
        }
        return false
    }


    override fun constructorArgumentNames(className: ClassName): List<String> {
        for (delegate in delegates) {
            if (delegate.contains(className)) {
                return delegate.constructorArgumentNames(className)
            }
        }
        throw IllegalArgumentException("Unknown: $className")
    }


    override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        for (delegate in delegates) {
            if (delegate.contains(className)) {
                return delegate.create(className, constructorArguments)
            }
        }
        throw IllegalArgumentException("Unknown: $className")
    }

}