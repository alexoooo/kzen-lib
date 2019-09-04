package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


/**
 * Path to a resource within a document
 */
data class ResourcePath(
        val resourceName: ResourceName,
        val resourceNesting: ResourceNesting
): Digestible {
    override fun digest(digester: Digest.Streaming) {
        digester.addDigestible(resourceName)
        digester.addDigestible(resourceNesting)
    }
}