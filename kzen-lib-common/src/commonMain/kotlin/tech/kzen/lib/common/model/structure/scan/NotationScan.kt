package tech.kzen.lib.common.model.structure.scan

import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class NotationScan(
        val documents: DocumentPathMap<DocumentScan>
): Digestible {
    private var digest: Digest? = null


    override fun digest(builder: Digest.Builder) {
        builder.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()
            builder.addDigestibleUnorderedMap(
                    documents.values)
            digest = builder.digest()
        }
        return digest!!
    }
}