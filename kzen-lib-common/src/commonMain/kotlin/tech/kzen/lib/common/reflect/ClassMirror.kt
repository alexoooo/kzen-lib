package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName


interface ClassMirror {
    fun contains(className: ClassName): Boolean

    fun constructorArgumentNames(className: ClassName): List<String>

    fun create(className: ClassName, constructorArguments: List<Any?>): Any
}