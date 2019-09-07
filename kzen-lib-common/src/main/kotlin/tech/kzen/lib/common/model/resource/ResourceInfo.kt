package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceInfo(
        val contentLength: Int,
        val contentDigest: Digest
): Digestible {
    override fun digest(digester: Digest.Builder) {
        digester.addDigest(contentDigest)
    }
}