package tech.kzen.lib.common.structure.notation.io.model

import tech.kzen.lib.common.model.resource.ResourceListing
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DocumentScan(
        val documentDigest: Digest,
        val resources: ResourceListing?
): Digestible {
    override fun digest(digester: Digest.Streaming) {
        digester.addDigest(documentDigest)
        digester.addDigestible(resources)
    }
}