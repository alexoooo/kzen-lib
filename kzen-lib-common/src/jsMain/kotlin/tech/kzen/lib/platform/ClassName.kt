package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


actual data class ClassName actual constructor(
    private val asString: String
):
    Digestible
{
//    actual companion object {
//        actual fun ofString(asString: String): ClassName {
//            return ClassName(asString)
//        }
//    }


    actual fun get(): String {
        return asString.replace('$', '.')
    }


    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(asString)
    }


    actual fun asString(): String {
        return asString
    }


    override fun toString(): String {
        return asString
    }
}