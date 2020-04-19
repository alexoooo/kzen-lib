package tech.kzen.lib.platform

import tech.kzen.lib.common.reflect.ClassMirror


expect object PlatformMirror: ClassMirror {
    override fun contains(className: ClassName): Boolean

    override fun constructorArgumentNames(className: ClassName): List<String>

    override fun create(className: ClassName, constructorArguments: List<Any?>): Any
}