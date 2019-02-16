package tech.kzen.lib.common.structure.metadata.model


data class TypeMetadata(
        val className: String,
        val generics: List<TypeMetadata>)