package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceName(
        val value: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Builder) {
        digester.addUtf8(value)
    }
}