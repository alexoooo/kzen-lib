package tech.kzen.lib.common.notation.model


sealed class ParameterNotation


data class ScalarParameterNotation(
        val value: Any?
) : ParameterNotation()


data class ListParameterNotation(
        val values: List<ParameterNotation>
) : ParameterNotation()


data class MapParameterNotation(
        val values: Map<String, ParameterNotation>
) : ParameterNotation()

