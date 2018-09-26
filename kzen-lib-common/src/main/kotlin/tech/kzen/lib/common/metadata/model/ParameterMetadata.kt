package tech.kzen.lib.common.metadata.model


data class ParameterMetadata(
        val type: TypeMetadata?,
//        val defaultValue: ParameterNotation?,
        val definer: String?,
        val creator: String?)
