package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName


interface ModuleReflection {
    companion object  {
        val simpleName: String = ModuleReflection::class.simpleName!!
        val qualifiedName = "tech.kzen.lib.common.reflect.$simpleName"
        val className = ClassName(qualifiedName)
    }

    fun register(reflectionRegistry: ReflectionRegistry = ReflectionRegistry.global)
}