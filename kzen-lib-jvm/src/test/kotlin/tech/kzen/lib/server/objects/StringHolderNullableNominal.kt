package tech.kzen.lib.server.objects

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
data class StringHolderNullableNominal(
    val stringHolderOrNull: ObjectLocation?
)