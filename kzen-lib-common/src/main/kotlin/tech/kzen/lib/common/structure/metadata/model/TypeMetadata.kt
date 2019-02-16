package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.platform.ClassName


data class TypeMetadata(
        val className: ClassName,
        val generics: List<TypeMetadata>)