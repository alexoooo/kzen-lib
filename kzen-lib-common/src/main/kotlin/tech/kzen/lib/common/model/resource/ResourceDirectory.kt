package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceDirectory(
        val value: String
): Digestible {
    override fun digest(digester: Digest.Builder) {
        digester.addUtf8(value)
    }
}