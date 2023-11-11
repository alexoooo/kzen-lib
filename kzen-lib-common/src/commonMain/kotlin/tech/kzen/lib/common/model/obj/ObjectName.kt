package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ObjectName(
    val value: String
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(value.isNotEmpty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }
}