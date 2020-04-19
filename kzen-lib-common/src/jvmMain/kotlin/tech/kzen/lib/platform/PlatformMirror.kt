package tech.kzen.lib.platform

import tech.kzen.lib.common.reflect.ClassMirror
import kotlin.reflect.full.primaryConstructor


actual object PlatformMirror: ClassMirror {
    actual override fun contains(className: ClassName): Boolean {
        return try {
            Class.forName(className.get())
            true
        }
        catch (e: ClassNotFoundException) {
            false
        }
    }


    actual override fun constructorArgumentNames(className: ClassName): List<String> {
        val type = Class.forName(className.get()).kotlin
        return type.primaryConstructor!!.parameters.map { it.name!! }
    }


    actual override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        try {
            val type = Class.forName(className.get()).kotlin
            return type.primaryConstructor!!.call(*constructorArguments.toTypedArray())
        }
        catch (e: Throwable) {
            throw IllegalArgumentException("Unable to create $className - $constructorArguments", e)
        }
    }
}