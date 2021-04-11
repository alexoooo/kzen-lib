package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AttributeName(
        val value: String
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    fun asAttributePath(): AttributePath {
        return AttributePath.ofName(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return value
    }
}