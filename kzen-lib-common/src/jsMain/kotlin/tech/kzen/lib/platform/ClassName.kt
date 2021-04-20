package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


actual data class ClassName actual constructor(
    private val jvmClassName: String
):
    Digestible
{
    actual fun get(): String {
        return jvmClassName.replace('$', '.')
    }


    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(jvmClassName)
    }


    override fun toString(): String {
        return jvmClassName
    }
}