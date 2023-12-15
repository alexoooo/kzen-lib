package tech.kzen.lib.common.model.structure.scan

import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class NotationScan(
    val documents: DocumentPathMap<DocumentScan>
): Digestible {
    private var digest: Digest? = null


    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()
            builder.addDigestibleUnorderedMap(
                    documents.map)
            digest = builder.digest()
        }
        return digest!!
    }
}