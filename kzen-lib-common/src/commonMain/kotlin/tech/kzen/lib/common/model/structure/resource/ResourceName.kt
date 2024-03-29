package tech.kzen.lib.common.model.structure.resource

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ResourceName(
        val value: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }
}