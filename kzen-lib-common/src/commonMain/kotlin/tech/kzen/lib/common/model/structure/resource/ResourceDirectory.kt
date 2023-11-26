package tech.kzen.lib.common.model.structure.resource

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ResourceDirectory(
    val value: String
): Digestible {
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }
}