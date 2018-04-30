package tech.kzen.lib.common.definition


data class ObjectDefinition(
        val className: String,
        val constructorArguments: Map<String, ParameterDefinition>,
        val creator: String,
        val creatorReferences: Set<String>
) {
    fun references(): Set<String> {
        val builder = mutableSetOf<String>()

        for (e in constructorArguments) {
            builder.addAll(e.value.references())
        }

        builder.add(creator)
        builder.addAll(creatorReferences)

        return builder
    }
}