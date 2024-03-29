package tech.kzen.lib.common.model.structure.scan

import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class DocumentScan(
    val documentDigest: Digest,
    val resources: ResourceListing?
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigest(documentDigest)
        sink.addDigestibleNullable(resources)
    }
}