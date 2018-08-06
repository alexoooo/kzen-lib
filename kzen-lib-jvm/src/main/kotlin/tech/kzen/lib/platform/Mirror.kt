package tech.kzen.lib.platform

import kotlin.reflect.full.primaryConstructor


actual object Mirror {
    actual fun contains(className: String): Boolean {
        return try {
            Class.forName(className)
            true
        }
        catch (e: ClassNotFoundException) {
            false
        }
    }

    actual fun constructorArgumentNames(className: String): List<String> {
        val type = Class.forName(className).kotlin
        return type.primaryConstructor!!.parameters.map { it.name!! }
    }

//    actual fun singletonClassNames(): List<String> {
//        TODO()
//    }

    actual fun create(className: String, constructorArguments: List<Any?>): Any {
        val type = Class.forName(className).kotlin
        return type.primaryConstructor!!.call(*constructorArguments.toTypedArray())
    }
}