package tech.kzen.lib.common.model.structure.scan

import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class NotationScan(
        val documents: DocumentPathMap<DocumentScan>
): Digestible {
    private var digestCache: Digest? = null


    override fun digest(builder: Digest.Builder) {
        builder.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digestCache == null) {
            val builder = Digest.Builder()
            builder.addDigestibleUnorderedMap(
                    documents.values)
            digestCache = builder.digest()
        }
        return digestCache!!
    }
}