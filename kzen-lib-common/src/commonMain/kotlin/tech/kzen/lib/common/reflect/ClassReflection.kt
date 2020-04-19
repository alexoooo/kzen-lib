package tech.kzen.lib.common.reflect


data class ClassReflection(
        val constructorArgumentNames: List<String>,
        val constructorFunction: (List<Any?>) -> Any
)