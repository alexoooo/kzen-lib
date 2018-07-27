package tech.kzen.lib.common.notation.model


//---------------------------------------------------------------------------------------------------------------------
sealed class ParameterNotation {
    fun asString(): String? {
        return (this as? ScalarParameterNotation)
                ?.value
                as? String
    }

    fun asBoolean(): Boolean? {
        return (this as? ScalarParameterNotation)
                ?.value
                as? Boolean
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class ScalarParameterNotation(
        val value: Any?
) : ParameterNotation()


abstract sealed class StructuredParameterNotation : ParameterNotation() {
    abstract fun get(key: String): ParameterNotation?
}


data class ListParameterNotation(
        val values: List<ParameterNotation>
) : StructuredParameterNotation() {
    override fun get(key: String): ParameterNotation? {
        val index = key.toInt()
        return values[index]
    }
}


data class MapParameterNotation(
        val values: Map<String, ParameterNotation>
) : StructuredParameterNotation() {
    override fun get(key: String): ParameterNotation? {
        return values[key]
    }
}

