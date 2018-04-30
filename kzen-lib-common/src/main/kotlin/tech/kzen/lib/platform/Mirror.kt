package tech.kzen.lib.platform


expect object Mirror {
    fun contains(className: String): Boolean

    fun constructorArgumentNames(className: String): List<String>

//    fun singletonClassNames(): List<String>

    fun create(className: String, constructorArguments: List<Any?>): Any
}