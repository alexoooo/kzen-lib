package tech.kzen.lib.common.metadata.model


data class AttributeMetadata(
        val type: TypeMetadata?,
//        val defaultValue: ParameterNotation?,
        val definer: String?,
        val creator: String?)
