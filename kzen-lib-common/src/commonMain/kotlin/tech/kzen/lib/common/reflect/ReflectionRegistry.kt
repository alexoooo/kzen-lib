package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName


// TODO: make threadsafe, see https://discuss.kotlinlang.org/t/replacement-for-synchronized/11240/3
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
        return registry[className]
    }


    fun put(className: String,
            constructorArgumentNames: List<String>,
            constructorFunction: (List<Any?>) -> Any
    ) {
        registry[ClassName(className)] = ClassReflection(constructorArgumentNames, constructorFunction)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(className: ClassName): Boolean {
        return get(className) != null
    }


    override fun constructorArgumentNames(className: ClassName): List<String> {
        return get(className)?.constructorArgumentNames
                ?: throw IllegalArgumentException("Not found: $className")
    }


    override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        return get(className)?.constructorFunction?.invoke(constructorArguments)
                ?: throw IllegalArgumentException("Not found: $className")
    }
}