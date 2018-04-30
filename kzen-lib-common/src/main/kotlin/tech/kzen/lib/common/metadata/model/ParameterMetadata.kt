package tech.kzen.lib.common.metadata.model

import tech.kzen.lib.common.notation.model.ParameterNotation


data class ParameterMetadata(
        val type: TypeMetadata?,
        val defaultValue: ParameterNotation?,
        val definer: String?,
        val creator: String?)
