package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName


data class ArgumentReflection(
    val name: String,
    val type: String,
    val typeClasses: Set<ClassName>
)