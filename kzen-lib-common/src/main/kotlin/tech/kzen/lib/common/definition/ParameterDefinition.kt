package tech.kzen.lib.common.definition


sealed class ParameterDefinition {
    abstract fun references(): Set<String>
}


data class ValueParameterDefinition(
        val value: Any?
) : ParameterDefinition() {
    override fun references(): Set<String> = setOf()
}


data class ReferenceParameterDefinition(
        val objectName: String?
) : ParameterDefinition() {
    override fun references(): Set<String> =
            if (objectName == null) {
                setOf()
            } else {
                setOf(objectName)
            }
}


// TODO: should this be CollectionParameterDefinition or something else that's more generic?
data class ListParameterDefinition(
        val values: List<ParameterDefinition>
) : ParameterDefinition() {
    override fun references(): Set<String> {
        val builder = mutableSetOf<String>()
        for (value in values) {
            builder.addAll(value.references())
        }
        return builder
    }
}


data class MapParameterDefinition(
        val values: Map<String, ParameterDefinition>
) : ParameterDefinition() {
    override fun references(): Set<String> {
        val builder = mutableSetOf<String>()
        for (value in values.values) {
            builder.addAll(value.references())
        }
        return builder
    }
}
