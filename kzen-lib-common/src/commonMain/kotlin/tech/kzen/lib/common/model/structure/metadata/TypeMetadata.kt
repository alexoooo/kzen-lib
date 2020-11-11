package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames


data class TypeMetadata(
        val className: ClassName,
        val generics: List<TypeMetadata>
) {
    companion object {
        val any = of(ClassNames.kotlinAny)
        val string = of(ClassNames.kotlinString)
        val int = of(ClassNames.kotlinInt)
        val long = of(ClassNames.kotlinList)
        val double = of(ClassNames.kotlinDouble)

        fun of(className: ClassName): TypeMetadata {
            return TypeMetadata(
                className,
                listOf())
        }
    }
}