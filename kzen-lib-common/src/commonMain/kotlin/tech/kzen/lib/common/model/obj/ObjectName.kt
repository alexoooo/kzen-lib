package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ObjectName(
    val value: String
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val main = ObjectName("main")
    }


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