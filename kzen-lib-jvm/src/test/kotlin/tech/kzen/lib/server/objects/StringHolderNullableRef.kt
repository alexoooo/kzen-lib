package tech.kzen.lib.server.objects

import tech.kzen.lib.common.reflect.Reflect


@Reflect
data class StringHolderNullableRef(
    val stringHolderOrNull: StringHolder?
)