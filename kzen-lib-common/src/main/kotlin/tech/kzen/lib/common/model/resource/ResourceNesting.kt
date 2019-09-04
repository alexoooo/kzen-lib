package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceNesting(
        val directories: List<ResourceDirectory>
): Digestible {
    override fun digest(digester: Digest.Streaming) {
        digester.addDigestibleList(directories)
    }
}