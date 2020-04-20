package tech.kzen.lib.server.codegen


data class ConstructorReflection(
        val arguments: List<ArgumentReflection>,
        val isObject: Boolean
) {
    companion object {
        val emptyClass = ConstructorReflection(listOf(), false)
        val emptyObject = ConstructorReflection(listOf(), true)

        fun ofClass(arguments: List<ArgumentReflection>): ConstructorReflection {
            if (arguments.isEmpty()) {
                return emptyClass
            }

            return ConstructorReflection(arguments, false)
        }
    }
}