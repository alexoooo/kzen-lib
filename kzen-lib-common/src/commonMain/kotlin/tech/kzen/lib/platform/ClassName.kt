package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digestible


expect class ClassName(
    jvmClassName: String
):
    Digestible
{
    fun get(): String
}