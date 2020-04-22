package tech.kzen.lib.server.codegen


data class ConstructorReflection(
        val arguments: List<ArgumentReflection>,
        val typeParameters: List<String>,
        val isObject: Boolean
) {
    companion object {
        val emptyClass = ConstructorReflection(listOf(), listOf(), false)
        val emptyObject = ConstructorReflection(listOf(), listOf(), true)

        fun ofClass(
            arguments: List<ArgumentReflection>,
            typeParameters: List<String> = listOf()
        ): ConstructorReflection {
            if (arguments.isEmpty() && typeParameters.isEmpty()) {
                return emptyClass
            }

            return ConstructorReflection(arguments, typeParameters, false)
        }
    }
}