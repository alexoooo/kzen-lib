package tech.kzen.lib.server.codegen


data class ConstructorReflection(
        val arguments: List<Pair<String, String>>,
        val isObject: Boolean
) {
    companion object {
        val emptyClass = ConstructorReflection(listOf(), false)
        val emptyObject = ConstructorReflection(listOf(), true)

        fun ofClass(arguments: List<Pair<String, String>>): ConstructorReflection {
            if (arguments.isEmpty()) {
                return emptyClass
            }

            return ConstructorReflection(arguments, false)
        }
    }
}