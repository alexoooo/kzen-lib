package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ObjectName(
        val value: String
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(value)
    }
}