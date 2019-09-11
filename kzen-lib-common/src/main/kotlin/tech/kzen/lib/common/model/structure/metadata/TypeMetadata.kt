package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


data class TypeMetadata(
        val className: ClassName,
        val generics: List<TypeMetadata>
) {
    companion object {
        val any = TypeMetadata(
                ClassNames.kotlinString,
                listOf())
    }
}