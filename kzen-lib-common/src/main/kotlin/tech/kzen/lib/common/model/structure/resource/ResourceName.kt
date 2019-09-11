package tech.kzen.lib.common.model.structure.resource

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
    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(value)
    }
}