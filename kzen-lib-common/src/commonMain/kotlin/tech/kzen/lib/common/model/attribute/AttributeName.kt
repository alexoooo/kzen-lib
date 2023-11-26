package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


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
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }


    fun asString(): String {
        return AttributePath.encodeDelimiter(value)
    }


    override fun toString(): String {
        return asString()
    }
}