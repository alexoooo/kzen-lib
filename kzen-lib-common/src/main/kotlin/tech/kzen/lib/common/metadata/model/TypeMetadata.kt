package tech.kzen.lib.common.metadata.model


data class TypeMetadata(
        val className: String,
        val generics: List<TypeMetadata>)