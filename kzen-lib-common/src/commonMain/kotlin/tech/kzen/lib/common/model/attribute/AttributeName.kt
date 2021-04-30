package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AttributeName(
    val value: String
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): AttributeName {
            return AttributeName(AttributePath.decodeDelimiter(asString))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asAttributePath(): AttributePath {
        return AttributePath.ofName(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(value)
    }


    fun asString(): String {
        return AttributePath.encodeDelimiter(value)
    }


    override fun toString(): String {
        return asString()
    }
}