package tech.kzen.lib.common.exec.data.type


/** The name of a type definition in a [DataContract] (a class's qualified name for a native shape). */
data class DefinitionId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "Definition id must not be blank" }
    }

    override fun toString(): String = value
}
